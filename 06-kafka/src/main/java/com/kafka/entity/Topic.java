package com.kafka.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Topic entity representing a named stream of records.
 * 
 * Topics are the primary abstraction in Kafka:
 * - Messages are organized into topics
 * - Topics are partitioned for parallel processing
 * - Each partition is an ordered, immutable sequence
 * - Configurable retention and compaction policies
 */
@Entity
@Table(name = "topics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "topic_id")
    private UUID topicId;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "partition_count")
    @Builder.Default
    private Integer partitionCount = 1;

    @Column(name = "replication_factor")
    @Builder.Default
    private Integer replicationFactor = 1;

    @Column(name = "min_insync_replicas")
    @Builder.Default
    private Integer minInsyncReplicas = 1;

    @Column(name = "retention_ms")
    @Builder.Default
    private Long retentionMs = 604800000L; // 7 days

    @Column(name = "retention_bytes")
    @Builder.Default
    private Long retentionBytes = -1L; // unlimited

    @Column(name = "segment_bytes")
    @Builder.Default
    private Long segmentBytes = 1073741824L; // 1GB

    @Column(name = "segment_ms")
    @Builder.Default
    private Long segmentMs = 604800000L; // 7 days

    @Column(name = "cleanup_policy")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CleanupPolicy cleanupPolicy = CleanupPolicy.DELETE;

    @Column(name = "compression_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CompressionType compressionType = CompressionType.PRODUCER;

    @Column(name = "max_message_bytes")
    @Builder.Default
    private Integer maxMessageBytes = 1048576; // 1MB

    @Column(name = "message_timestamp_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TimestampType messageTimestampType = TimestampType.CREATE_TIME;

    @Column(name = "is_internal")
    @Builder.Default
    private Boolean isInternal = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TopicStatus status = TopicStatus.ACTIVE;

    @Column(columnDefinition = "jsonb")
    private String config;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum CleanupPolicy {
        DELETE, // Delete old segments based on retention
        COMPACT, // Keep only latest value per key
        DELETE_COMPACT // Both delete and compact
    }

    public enum CompressionType {
        NONE,
        GZIP,
        SNAPPY,
        LZ4,
        ZSTD,
        PRODUCER // Use producer's compression
    }

    public enum TimestampType {
        CREATE_TIME, // Timestamp set by producer
        LOG_APPEND_TIME // Timestamp set by broker
    }

    public enum TopicStatus {
        ACTIVE,
        DELETING,
        MARKED_FOR_DELETION
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isCompacted() {
        return cleanupPolicy == CleanupPolicy.COMPACT ||
                cleanupPolicy == CleanupPolicy.DELETE_COMPACT;
    }
}
