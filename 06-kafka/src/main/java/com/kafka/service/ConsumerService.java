package com.kafka.service;

import com.kafka.entity.ConsumerGroup;
import com.kafka.entity.ConsumerGroupMember;
import com.kafka.entity.ConsumerOffset;
import com.kafka.repository.ConsumerGroupMemberRepository;
import com.kafka.repository.ConsumerGroupRepository;
import com.kafka.repository.ConsumerOffsetRepository;
import com.kafka.storage.Log;
import com.kafka.storage.Record;
import com.kafka.storage.RecordBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Consumer Service - Handles message consumption from Kafka.
 * 
 * Key responsibilities:
 * 1. Fetch messages from partitions
 * 2. Manage consumer group membership
 * 3. Handle offset commits
 * 4. Coordinate partition assignment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerService {

    private final LogManager logManager;
    private final TopicService topicService;
    private final ConsumerGroupRepository groupRepository;
    private final ConsumerGroupMemberRepository memberRepository;
    private final ConsumerOffsetRepository offsetRepository;
    private final GroupCoordinator groupCoordinator;

    @Value("${kafka.consumer.fetch.max.bytes:52428800}")
    private int fetchMaxBytes;

    @Value("${kafka.consumer.fetch.max.wait.ms:500}")
    private long fetchMaxWaitMs;

    @Value("${kafka.consumer.max.poll.records:500}")
    private int maxPollRecords;

    @Value("${kafka.consumer.session.timeout.ms:45000}")
    private int sessionTimeoutMs;

    /**
     * Subscribe to topics for a consumer group.
     */
    @Transactional
    public SubscriptionResult subscribe(String groupId, String memberId, String clientId,
            List<String> topics, String assignmentStrategy) {
        log.info("Consumer {} subscribing to topics {} in group {}", memberId, topics, groupId);

        // Validate topics exist
        for (String topic : topics) {
            topicService.getTopic(topic); // Throws if not found
        }

        // Get or create consumer group
        ConsumerGroup group = groupRepository.findByGroupId(groupId)
                .orElseGet(() -> createGroup(groupId));

        // Register member
        ConsumerGroupMember member = registerMember(group, memberId, clientId, topics);

        // Trigger rebalance if needed
        if (group.getState() == ConsumerGroup.GroupState.STABLE) {
            groupCoordinator.triggerRebalance(groupId);
        }

        return new SubscriptionResult(
                group.getGroupId(),
                member.getMemberId(),
                group.getGenerationId(),
                group.getState().name());
    }

    /**
     * Fetch messages from assigned partitions.
     */
    public FetchResult fetch(String groupId, String memberId,
            Map<TopicPartition, Long> fetchOffsets,
            int maxBytes, long maxWaitMs) throws IOException {

        // Validate member is active
        ConsumerGroupMember member = memberRepository.findByGroupGroupIdAndMemberId(groupId, memberId)
                .orElseThrow(() -> new UnknownMemberException("Unknown member: " + memberId));

        // Update heartbeat
        memberRepository.updateHeartbeat(memberId, Instant.now());

        List<FetchPartitionResult> results = new ArrayList<>();
        int totalBytes = 0;
        long startTime = System.currentTimeMillis();

        for (Map.Entry<TopicPartition, Long> entry : fetchOffsets.entrySet()) {
            if (totalBytes >= maxBytes)
                break;

            TopicPartition tp = entry.getKey();
            long fetchOffset = entry.getValue();

            try {
                Log partitionLog = logManager.getLog(tp.topic(), tp.partition());

                // Read records starting from fetchOffset
                Log.FetchResult fetchResult = partitionLog.read(
                        fetchOffset,
                        Math.min(maxBytes - totalBytes, fetchMaxBytes));
                List<RecordBatch> batches = convertToRecordBatches(fetchResult);

                long highWatermark = partitionLog.getHighWatermark();
                long logEndOffset = partitionLog.getLogEndOffset().get();

                FetchPartitionResult result = new FetchPartitionResult(
                        tp.topic(),
                        tp.partition(),
                        null,
                        highWatermark,
                        logEndOffset,
                        batches);
                results.add(result);

                // Calculate bytes fetched
                for (RecordBatch batch : batches) {
                    totalBytes += batch.sizeInBytes();
                }

            } catch (Exception e) {
                log.error("Error fetching from {}", tp, e);
                results.add(new FetchPartitionResult(
                        tp.topic(),
                        tp.partition(),
                        e.getMessage(),
                        -1,
                        -1,
                        Collections.emptyList()));
            }
        }

        // Wait if we got no data and maxWaitMs > 0
        if (results.isEmpty() && maxWaitMs > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed < maxWaitMs) {
                try {
                    Thread.sleep(Math.min(100, maxWaitMs - elapsed));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return new FetchResult(results);
    }

    /**
     * Commit offsets for a consumer.
     */
    @Transactional
    public void commitOffsets(String groupId, String memberId,
            Map<TopicPartition, OffsetAndMetadata> offsets) {
        log.debug("Committing offsets for {} in group {}: {}", memberId, groupId, offsets);

        // Validate member
        ConsumerGroupMember member = memberRepository.findByGroupGroupIdAndMemberId(groupId, memberId)
                .orElseThrow(() -> new UnknownMemberException("Unknown member: " + memberId));

        // Validate generation
        ConsumerGroup group = groupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new UnknownMemberException("Unknown group: " + groupId));

        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
            TopicPartition tp = entry.getKey();
            OffsetAndMetadata offsetMeta = entry.getValue();

            // Check if offset entry exists
            Optional<ConsumerOffset> existing = offsetRepository
                    .findByGroupTopicPartition(groupId, tp.topic(), tp.partition());

            if (existing.isPresent()) {
                offsetRepository.updateOffset(
                        groupId,
                        tp.topic(),
                        tp.partition(),
                        offsetMeta.offset(),
                        offsetMeta.metadata(),
                        Instant.now(),
                        offsetMeta.leaderEpoch());
            } else {
                ConsumerOffset offset = new ConsumerOffset();
                offset.setGroupId(groupId);
                offset.setTopicName(tp.topic());
                offset.setPartitionNumber(tp.partition());
                offset.setCommittedOffset(offsetMeta.offset());
                offset.setMetadata(offsetMeta.metadata());
                offset.setLeaderEpoch(offsetMeta.leaderEpoch());
                offset.setCommitTimestamp(LocalDateTime.now());
                offsetRepository.save(offset);
            }
        }

        log.info("Committed {} offsets for member {} in group {}",
                offsets.size(), memberId, groupId);
    }

    /**
     * Get committed offset for a topic-partition.
     */
    @Transactional(readOnly = true)
    public OffsetAndMetadata getCommittedOffset(String groupId, String topic, int partition) {
        return offsetRepository.findByGroupTopicPartition(groupId, topic, partition)
                .map(o -> new OffsetAndMetadata(o.getCommittedOffset(), o.getLeaderEpoch(), o.getMetadata()))
                .orElse(null);
    }

    /**
     * Get all committed offsets for a group.
     */
    @Transactional(readOnly = true)
    public Map<TopicPartition, OffsetAndMetadata> getCommittedOffsets(String groupId) {
        List<ConsumerOffset> offsets = offsetRepository.findByGroupId(groupId);
        Map<TopicPartition, OffsetAndMetadata> result = new HashMap<>();

        for (ConsumerOffset offset : offsets) {
            result.put(
                    new TopicPartition(offset.getTopicName(), offset.getPartitionNumber()),
                    new OffsetAndMetadata(offset.getCommittedOffset(),
                            offset.getLeaderEpoch(), offset.getMetadata()));
        }

        return result;
    }

    /**
     * Leave a consumer group.
     */
    @Transactional
    public void leaveGroup(String groupId, String memberId) {
        log.info("Member {} leaving group {}", memberId, groupId);

        memberRepository.deleteByMemberId(memberId);

        // Trigger rebalance
        ConsumerGroup group = groupRepository.findByGroupId(groupId).orElse(null);
        if (group != null && group.getState() == ConsumerGroup.GroupState.STABLE) {
            groupCoordinator.triggerRebalance(groupId);
        }
    }

    /**
     * Send heartbeat from consumer.
     */
    @Transactional
    public HeartbeatResult heartbeat(String groupId, String memberId, int generation) {
        ConsumerGroupMember member = memberRepository.findByGroupGroupIdAndMemberId(groupId, memberId)
                .orElseThrow(() -> new UnknownMemberException("Unknown member: " + memberId));

        ConsumerGroup group = groupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new UnknownMemberException("Unknown group: " + groupId));

        // Check generation
        if (generation != group.getGenerationId()) {
            return new HeartbeatResult(
                    HeartbeatResult.ErrorCode.REBALANCE_IN_PROGRESS,
                    "Generation mismatch: expected " + group.getGenerationId());
        }

        // Update heartbeat
        memberRepository.updateHeartbeat(memberId, Instant.now());

        return new HeartbeatResult(HeartbeatResult.ErrorCode.NONE, null);
    }

    /**
     * Seek to a specific offset.
     */
    public void seek(String groupId, TopicPartition tp, long offset) throws IOException {
        // Validate offset
        Log partitionLog = logManager.getLog(tp.topic(), tp.partition());
        long logStart = partitionLog.getLogStartOffset().get();
        long logEnd = partitionLog.getLogEndOffset().get();

        if (offset < logStart) {
            throw new OffsetOutOfRangeException(
                    "Offset " + offset + " is before log start " + logStart);
        }
        if (offset > logEnd) {
            throw new OffsetOutOfRangeException(
                    "Offset " + offset + " is after log end " + logEnd);
        }

        // Update the committed offset (or just track in-memory for this consumer)
        log.debug("Seeking {} to offset {} for group {}", tp, offset, groupId);
    }

    /**
     * Seek to beginning of topic-partition.
     */
    public void seekToBeginning(String groupId, TopicPartition tp) throws IOException {
        Log partitionLog = logManager.getLog(tp.topic(), tp.partition());
        seek(groupId, tp, partitionLog.getLogStartOffset().get());
    }

    /**
     * Seek to end of topic-partition.
     */
    public void seekToEnd(String groupId, TopicPartition tp) throws IOException {
        Log partitionLog = logManager.getLog(tp.topic(), tp.partition());
        seek(groupId, tp, partitionLog.getLogEndOffset().get());
    }

    /**
     * Get consumer group lag.
     */
    @Transactional(readOnly = true)
    public Map<TopicPartition, Long> getConsumerLag(String groupId) {
        Map<TopicPartition, Long> lag = new HashMap<>();
        List<ConsumerOffset> offsets = offsetRepository.findByGroupId(groupId);

        for (ConsumerOffset offset : offsets) {
            Long partitionLag = offsetRepository.calculateLag(
                    groupId, offset.getTopicName(), offset.getPartitionNumber());
            if (partitionLag != null) {
                lag.put(new TopicPartition(offset.getTopicName(), offset.getPartitionNumber()), partitionLag);
            }
        }

        return lag;
    }

    private ConsumerGroup createGroup(String groupId) {
        ConsumerGroup group = new ConsumerGroup();
        group.setGroupId(groupId);
        group.setState(ConsumerGroup.GroupState.EMPTY);
        group.setGenerationId(0);
        group.setProtocolType("consumer");
        return groupRepository.save(group);
    }

    private ConsumerGroupMember registerMember(ConsumerGroup group, String memberId,
            String clientId, List<String> topics) {
        Optional<ConsumerGroupMember> existing = memberRepository.findByGroupGroupIdAndMemberId(group.getGroupId(),
                memberId);

        ConsumerGroupMember member;
        if (existing.isPresent()) {
            member = existing.get();
            member.setClientId(clientId);
            member.setSubscribedTopics(topics);
            member.setLastHeartbeat(LocalDateTime.now());
        } else {
            member = new ConsumerGroupMember();
            member.setGroupId(group.getGroupId());
            member.setMemberId(memberId);
            member.setClientId(clientId);
            member.setSubscribedTopics(topics);
            member.setSessionTimeoutMs(sessionTimeoutMs);
            member.setLastHeartbeat(LocalDateTime.now());
        }

        return memberRepository.save(member);
    }

    /**
     * Convert Log.FetchResult to List<RecordBatch> for compatibility.
     */
    private List<RecordBatch> convertToRecordBatches(Log.FetchResult fetchResult) {
        if (fetchResult.getRecords() == null || fetchResult.getRecords().isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Record> records = fetchResult.getRecords();
        RecordBatch batch = RecordBatch.builder()
                .baseOffset(records.get(0).getOffset())
                .firstTimestamp(records.get(0).getTimestamp())
                .maxTimestamp(records.stream().mapToLong(Record::getTimestamp).max().orElse(0L))
                .records(records)
                .build();
        
        return List.of(batch);
    }

    // DTOs
    public record TopicPartition(String topic, int partition) {
    }

    public record OffsetAndMetadata(long offset, Integer leaderEpoch, String metadata) {
        public OffsetAndMetadata(long offset) {
            this(offset, null, null);
        }
    }

    public record SubscriptionResult(
            String groupId,
            String memberId,
            int generation,
            String state) {
    }

    public record FetchResult(List<FetchPartitionResult> results) {
    }

    public record FetchPartitionResult(
            String topic,
            int partition,
            String errorMessage,
            long highWatermark,
            long logEndOffset,
            List<RecordBatch> batches) {
    }

    public record HeartbeatResult(ErrorCode errorCode, String message) {
        public enum ErrorCode {
            NONE,
            REBALANCE_IN_PROGRESS,
            UNKNOWN_MEMBER,
            ILLEGAL_GENERATION
        }
    }

    // Exceptions
    public static class UnknownMemberException extends RuntimeException {
        public UnknownMemberException(String message) {
            super(message);
        }
    }

    public static class OffsetOutOfRangeException extends RuntimeException {
        public OffsetOutOfRangeException(String message) {
            super(message);
        }
    }
}
