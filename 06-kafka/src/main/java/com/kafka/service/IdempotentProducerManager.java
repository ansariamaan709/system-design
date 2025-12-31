package com.kafka.service;

import com.kafka.entity.ProducerId;
import com.kafka.repository.ProducerIdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Idempotent Producer Manager - Ensures exactly-once delivery semantics.
 * 
 * Key concepts:
 * 1. Producer ID (PID): Unique identifier for each producer instance
 * 2. Producer Epoch: Incremented on each init, used to fence zombies
 * 3. Sequence Number: Per-partition sequence for detecting duplicates
 * 
 * How idempotence works:
 * - Producer gets a unique PID from the broker
 * - Each record has a monotonically increasing sequence number
 * - Broker tracks last sequence per PID/partition
 * - Duplicates are detected by comparing sequences
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentProducerManager {

    private final ProducerIdRepository producerIdRepository;

    // In-memory cache of producer state for fast lookups
    private final Map<Long, ProducerStateEntry> producerStateCache = new ConcurrentHashMap<>();

    // Counter for generating new producer IDs
    private final AtomicLong nextProducerId = new AtomicLong(0);

    /**
     * Initialize a new producer and get a Producer ID.
     * Called when producer starts with enable.idempotence=true.
     */
    @Transactional
    public ProducerIdResult initProducerId(String transactionalId, int transactionTimeoutMs) {
        if (transactionalId != null && !transactionalId.isEmpty()) {
            return initTransactionalProducer(transactionalId, transactionTimeoutMs);
        }
        return initIdempotentProducer();
    }

    /**
     * Initialize an idempotent (non-transactional) producer.
     */
    private ProducerIdResult initIdempotentProducer() {
        long producerId = generateProducerId();
        short epoch = 0;

        ProducerId entity = new ProducerId();
        entity.setProducerId(producerId);
        entity.setProducerEpoch(epoch);
        entity.setLastSequenceNumber(-1);
        producerIdRepository.save(entity);

        // Cache the state
        producerStateCache.put(producerId, new ProducerStateEntry(producerId, epoch));

        log.info("Initialized idempotent producer with ID {}", producerId);
        return new ProducerIdResult(producerId, epoch);
    }

    /**
     * Initialize a transactional producer.
     * If transactionalId exists, bump epoch (fence old producer).
     */
    private ProducerIdResult initTransactionalProducer(String transactionalId, int timeoutMs) {
        Optional<ProducerId> existing = producerIdRepository.findByTransactionalId(transactionalId);

        if (existing.isPresent()) {
            // Existing producer - bump epoch to fence old instances
            ProducerId producer = existing.get();
            short newEpoch = (short) (producer.getProducerEpoch() + 1);

            if (newEpoch < 0) {
                // Epoch overflow - need new producer ID
                return handleEpochOverflow(transactionalId, timeoutMs);
            }

            producer.setProducerEpoch(newEpoch);
            producer.setLastSequenceNumber(-1);
            producer.setTransactionTimeoutMs(timeoutMs);
            producerIdRepository.save(producer);

            // Update cache
            producerStateCache.put(producer.getProducerId(),
                    new ProducerStateEntry(producer.getProducerId(), newEpoch));

            log.info("Bumped epoch for transactional producer {} to {}",
                    transactionalId, newEpoch);
            return new ProducerIdResult(producer.getProducerId(), newEpoch);
        }

        // New transactional producer
        long producerId = generateProducerId();
        short epoch = 0;

        ProducerId entity = new ProducerId();
        entity.setProducerId(producerId);
        entity.setProducerEpoch(epoch);
        entity.setTransactionalId(transactionalId);
        entity.setTransactionTimeoutMs(timeoutMs);
        entity.setLastSequenceNumber(-1);
        producerIdRepository.save(entity);

        producerStateCache.put(producerId, new ProducerStateEntry(producerId, epoch));

        log.info("Initialized transactional producer {} with ID {}", transactionalId, producerId);
        return new ProducerIdResult(producerId, epoch);
    }

    /**
     * Handle epoch overflow by generating new producer ID.
     */
    private ProducerIdResult handleEpochOverflow(String transactionalId, int timeoutMs) {
        long newProducerId = generateProducerId();
        short newEpoch = 0;

        // Update existing record with new producer ID
        Optional<ProducerId> existing = producerIdRepository.findByTransactionalId(transactionalId);
        if (existing.isPresent()) {
            ProducerId producer = existing.get();
            producerStateCache.remove(producer.getProducerId());
            producerIdRepository.delete(producer);
        }

        ProducerId entity = new ProducerId();
        entity.setProducerId(newProducerId);
        entity.setProducerEpoch(newEpoch);
        entity.setTransactionalId(transactionalId);
        entity.setTransactionTimeoutMs(timeoutMs);
        entity.setLastSequenceNumber(-1);
        producerIdRepository.save(entity);

        producerStateCache.put(newProducerId, new ProducerStateEntry(newProducerId, newEpoch));

        log.info("Generated new producer ID {} for {} due to epoch overflow",
                newProducerId, transactionalId);
        return new ProducerIdResult(newProducerId, newEpoch);
    }

    /**
     * Check if a batch is a duplicate or out of sequence.
     * 
     * Returns:
     * - ACCEPTED: Batch is valid and should be written
     * - DUPLICATE: Batch is a duplicate and should be skipped
     * - OUT_OF_ORDER: Sequence gap detected, reject the batch
     */
    public SequenceCheckResult checkSequence(long producerId, short producerEpoch,
            int firstSequence, int lastSequence,
            String topic, int partition) {
        ProducerStateEntry state = producerStateCache.get(producerId);

        if (state == null) {
            // Unknown producer - load from database
            Optional<ProducerId> producer = producerIdRepository.findByProducerId(producerId);
            if (producer.isEmpty()) {
                return SequenceCheckResult.UNKNOWN_PRODUCER;
            }
            state = new ProducerStateEntry(producerId, producer.get().getProducerEpoch());
            producerStateCache.put(producerId, state);
        }

        // Check epoch
        if (producerEpoch < state.epoch()) {
            log.warn("Producer {} fenced: epoch {} < current {}",
                    producerId, producerEpoch, state.epoch());
            return SequenceCheckResult.FENCED;
        }
        if (producerEpoch > state.epoch()) {
            // This shouldn't happen - producer should init first
            return SequenceCheckResult.INVALID_EPOCH;
        }

        // Get last sequence for this topic-partition
        TopicPartitionKey tpKey = new TopicPartitionKey(topic, partition);
        Integer lastSeq = state.lastSequenceByPartition().get(tpKey);
        int expectedSeq = (lastSeq == null) ? 0 : lastSeq + 1;

        if (firstSequence == expectedSeq) {
            // Valid sequence - update state
            state.lastSequenceByPartition().put(tpKey, lastSequence);
            return SequenceCheckResult.ACCEPTED;
        }

        if (firstSequence < expectedSeq) {
            // Check if this is a full duplicate
            if (lastSequence < expectedSeq) {
                return SequenceCheckResult.DUPLICATE;
            }
            // Partial duplicate - reject
            return SequenceCheckResult.OUT_OF_ORDER;
        }

        // Sequence gap
        log.warn("Sequence gap for producer {}: expected {}, got {}",
                producerId, expectedSeq, firstSequence);
        return SequenceCheckResult.OUT_OF_ORDER;
    }

    /**
     * Update sequence after successful append.
     */
    public void updateSequence(long producerId, int lastSequence, String topic, int partition) {
        ProducerStateEntry state = producerStateCache.get(producerId);
        if (state != null) {
            state.lastSequenceByPartition().put(
                    new TopicPartitionKey(topic, partition), lastSequence);
        }

        // Also update persistent store
        producerIdRepository.updateSequence(producerId, lastSequence);
    }

    /**
     * Generate a new unique producer ID.
     */
    private long generateProducerId() {
        // First check max from database
        Long maxId = producerIdRepository.findMaxProducerId();
        if (maxId != null) {
            nextProducerId.updateAndGet(current -> Math.max(current, maxId + 1));
        }
        return nextProducerId.getAndIncrement();
    }

    /**
     * Expire old producer IDs to reclaim memory.
     */
    @Transactional
    public int expireProducers(long expirationMs) {
        LocalDateTime threshold = LocalDateTime.now().minusNanos(expirationMs * 1_000_000);

        // Remove from cache
        producerStateCache.entrySet().removeIf(entry -> {
            ProducerId producer = producerIdRepository.findByProducerId(entry.getKey()).orElse(null);
            return producer != null && producer.getLastUpdateTime() != null && producer.getLastUpdateTime().isBefore(threshold);
        });

        // Remove from database
        return producerIdRepository.deleteExpiredProducers(threshold);
    }

    // DTOs
    public record ProducerIdResult(long producerId, short epoch) {
    }

    public enum SequenceCheckResult {
        ACCEPTED,
        DUPLICATE,
        OUT_OF_ORDER,
        FENCED,
        INVALID_EPOCH,
        UNKNOWN_PRODUCER
    }

    private record TopicPartitionKey(String topic, int partition) {
    }

    private record ProducerStateEntry(
            long producerId,
            short epoch,
            Map<TopicPartitionKey, Integer> lastSequenceByPartition) {
        ProducerStateEntry(long producerId, short epoch) {
            this(producerId, epoch, new ConcurrentHashMap<>());
        }
    }
}
