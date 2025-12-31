package com.kafka.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumer Offset entity tracking committed offsets per consumer group.
 * 
 * Offsets are:
 * - The position of the next message to consume
 * - Committed by consumers after processing
 * - Stored in __consumer_offsets internal topic (and DB)
 * - Used for recovery after consumer restart
 * 
 * Offset commit semantics:
 * - Auto-commit: Periodic automatic commit
 * - Manual commit: Consumer controls when to commit
 * - Sync vs Async: Block vs fire-and-forget
 */
@Entity
@Table(name = "consumer_offsets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumerOffset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "offset_id")
    private UUID offsetId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(name = "partition_number", nullable = false)
    private Integer partitionNumber;

    /**
     * The committed offset - next message to be consumed.
     */
    @Column(name = "committed_offset", nullable = false)
    private Long committedOffset;

    /**
     * Leader epoch at the time of commit.
     * Used to fence outdated commits after leader change.
     */
    @Column(name = "leader_epoch")
    private Integer leaderEpoch;

    /**
     * Optional metadata stored with the commit.
     */
    @Column(length = 1000)
    private String metadata;

    @Column(name = "commit_timestamp")
    private LocalDateTime commitTimestamp;

    /**
     * When this offset record expires.
     * Offsets are retained for offsets.retention.minutes (default 7 days).
     */
    @Column(name = "expire_timestamp")
    private LocalDateTime expireTimestamp;

    @PrePersist
    protected void onCreate() {
        commitTimestamp = LocalDateTime.now();
        if (expireTimestamp == null) {
            // Default: 7 days retention
            expireTimestamp = LocalDateTime.now().plusDays(7);
        }
    }

    /**
     * Get the topic-partition key.
     */
    public String getTopicPartition() {
        return topicName + "-" + partitionNumber;
    }

    /**
     * Check if this offset has expired.
     */
    public boolean isExpired() {
        return expireTimestamp != null && LocalDateTime.now().isAfter(expireTimestamp);
    }

    /**
     * Update the committed offset.
     */
    public void updateOffset(long newOffset, Integer newLeaderEpoch) {
        this.committedOffset = newOffset;
        this.leaderEpoch = newLeaderEpoch;
        this.commitTimestamp = LocalDateTime.now();
        // Reset expiry
        this.expireTimestamp = LocalDateTime.now().plusDays(7);
    }
}
