package com.kafka.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Producer ID entity for idempotent producer support.
 * 
 * Idempotence ensures exactly-once delivery from producer to broker:
 * - Each producer gets a unique ProducerID (PID)
 * - Each message has a sequence number
 * - Broker deduplicates by checking (PID, sequence) pairs
 * 
 * Producer epoch is used to fence old producers:
 * - Incremented when producer restarts or InitProducerId called
 * - Old epoch producers are rejected
 */
@Entity
@Table(name = "producer_ids")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProducerId {

    @Id
    @Column(name = "producer_id")
    private Long producerId;

    /**
     * Producer epoch for fencing.
     * Incremented on each InitProducerId call.
     */
    @Column(name = "producer_epoch", nullable = false)
    @Builder.Default
    private Short producerEpoch = 0;

    /**
     * Transactional ID if this is a transactional producer.
     */
    @Column(name = "transactional_id")
    private String transactionalId;

    /**
     * Coordinator broker for this producer.
     */
    @Column(name = "coordinator_broker_id")
    private Integer coordinatorBrokerId;

    /**
     * Transaction timeout in milliseconds for transactional producers.
     */
    @Column(name = "transaction_timeout_ms")
    private Integer transactionTimeoutMs;

    /**
     * Last sequence number used by this producer.
     * Used for deduplication.
     */
    @Column(name = "last_sequence_number")
    @Builder.Default
    private Integer lastSequenceNumber = -1;

    @Column(name = "last_timestamp")
    private LocalDateTime lastTimestamp;

    @Column(name = "last_update_time")
    private LocalDateTime lastUpdateTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastTimestamp = LocalDateTime.now();
        lastUpdateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdateTime = LocalDateTime.now();
    }

    /**
     * Increment epoch and reset sequence.
     */
    public void incrementEpoch() {
        this.producerEpoch++;
        this.lastSequenceNumber = -1;
        this.lastTimestamp = LocalDateTime.now();
    }

    /**
     * Check if a sequence number is valid (next expected or duplicate).
     */
    public boolean isValidSequence(int sequence) {
        // First message or next in sequence
        if (lastSequenceNumber == -1 || sequence == lastSequenceNumber + 1) {
            return true;
        }
        // Duplicate (already seen)
        return sequence <= lastSequenceNumber;
    }

    /**
     * Check if this is a duplicate sequence number.
     */
    public boolean isDuplicate(int sequence) {
        return lastSequenceNumber != -1 && sequence <= lastSequenceNumber;
    }

    /**
     * Update last sequence number.
     */
    public void updateSequence(int sequence) {
        if (sequence > lastSequenceNumber) {
            this.lastSequenceNumber = sequence;
            this.lastTimestamp = LocalDateTime.now();
        }
    }
}
