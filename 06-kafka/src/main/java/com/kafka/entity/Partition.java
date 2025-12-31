package com.kafka.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Partition entity representing a single partition of a topic.
 * 
 * Partitions are the unit of parallelism in Kafka:
 * - Each partition is an ordered, immutable sequence of records
 * - Partitions are replicated across brokers for fault tolerance
 * - One replica is elected as leader, others are followers
 * - Consumers in a group are assigned specific partitions
 * 
 * Key concepts:
 * - LEO (Log End Offset): Next offset to be written
 * - HW (High Watermark): Offset committed to all ISR
 * - ISR (In-Sync Replicas): Replicas caught up to leader
 */
@Entity
@Table(name = "partitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "partition_id")
    private UUID partitionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "partition_number", nullable = false)
    private Integer partitionNumber;

    @Column(name = "leader_broker_id")
    private Integer leaderBrokerId;

    @Column(name = "leader_epoch")
    @Builder.Default
    private Integer leaderEpoch = 0;

    /**
     * In-Sync Replica broker IDs.
     * Replicas that are fully caught up with the leader.
     */
    @Column(name = "isr_broker_ids", columnDefinition = "integer[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<Integer> isrBrokerIds = new ArrayList<>();

    /**
     * All replica broker IDs (includes leader).
     */
    @Column(name = "replica_broker_ids", columnDefinition = "integer[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<Integer> replicaBrokerIds = new ArrayList<>();

    /**
     * Offline replica broker IDs.
     */
    @Column(name = "offline_replica_ids", columnDefinition = "integer[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<Integer> offlineReplicaIds = new ArrayList<>();

    /**
     * Log Start Offset - earliest available offset.
     * Messages before this have been deleted by retention.
     */
    @Column(name = "log_start_offset")
    @Builder.Default
    private Long logStartOffset = 0L;

    /**
     * Log End Offset (LEO) - next offset to be written.
     * This is the offset that will be assigned to the next message.
     */
    @Column(name = "log_end_offset")
    @Builder.Default
    private Long logEndOffset = 0L;

    /**
     * High Watermark (HW) - highest committed offset.
     * Messages up to this offset are visible to consumers.
     * HW <= LEO (leader) <= LEO (any replica)
     */
    @Column(name = "high_watermark")
    @Builder.Default
    private Long highWatermark = 0L;

    @Column(name = "active_segment_base_offset")
    @Builder.Default
    private Long activeSegmentBaseOffset = 0L;

    @Column(name = "segment_count")
    @Builder.Default
    private Integer segmentCount = 1;

    @Column(name = "log_size_bytes")
    @Builder.Default
    private Long logSizeBytes = 0L;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PartitionStatus status = PartitionStatus.ONLINE;

    @Column(name = "last_modified")
    private LocalDateTime lastModified;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum PartitionStatus {
        ONLINE,
        OFFLINE,
        UNDER_REPLICATED,
        REASSIGNING
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastModified = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastModified = LocalDateTime.now();
    }

    /**
     * Get the topic-partition identifier string.
     */
    public String getTopicPartition() {
        return topic.getName() + "-" + partitionNumber;
    }

    /**
     * Check if this broker is in the ISR.
     */
    public boolean isInIsr(int brokerId) {
        return isrBrokerIds != null && isrBrokerIds.contains(brokerId);
    }

    /**
     * Check if this broker is a replica.
     */
    public boolean isReplica(int brokerId) {
        return replicaBrokerIds != null && replicaBrokerIds.contains(brokerId);
    }

    /**
     * Check if this partition is under-replicated.
     */
    public boolean isUnderReplicated() {
        return isrBrokerIds == null ||
                replicaBrokerIds == null ||
                isrBrokerIds.size() < replicaBrokerIds.size();
    }

    /**
     * Add a broker to the ISR.
     */
    public void addToIsr(int brokerId) {
        if (isrBrokerIds == null) {
            isrBrokerIds = new ArrayList<>();
        }
        if (!isrBrokerIds.contains(brokerId)) {
            isrBrokerIds.add(brokerId);
        }
        offlineReplicaIds.remove(Integer.valueOf(brokerId));
    }

    /**
     * Remove a broker from the ISR.
     */
    public void removeFromIsr(int brokerId) {
        if (isrBrokerIds != null) {
            isrBrokerIds.remove(Integer.valueOf(brokerId));
        }
    }

    /**
     * Increment leader epoch (on leader change).
     */
    public void incrementLeaderEpoch() {
        this.leaderEpoch++;
    }

    /**
     * Get the number of messages in this partition.
     */
    public long getMessageCount() {
        return logEndOffset - logStartOffset;
    }

    /**
     * Update high watermark to new value if greater.
     */
    public void maybeUpdateHighWatermark(long newHw) {
        if (newHw > this.highWatermark) {
            this.highWatermark = newHw;
        }
    }
}
