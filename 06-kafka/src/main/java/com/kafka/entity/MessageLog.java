package com.kafka.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Message Log entity for storing individual records.
 * 
 * Note: In production Kafka, messages are stored in segment files on disk.
 * This database-backed implementation is for educational purposes and
 * smaller deployments. For high throughput, use the file-based Log class.
 * 
 * Record format follows Kafka's RecordBatch structure:
 * - Offset: Position in the partition
 * - Timestamp: CreateTime or LogAppendTime
 * - Key: Optional message key for partitioning and compaction
 * - Value: Message payload
 * - Headers: Optional key-value metadata
 */
@Entity
@Table(name = "message_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(name = "partition_number", nullable = false)
    private Integer partitionNumber;

    /**
     * Offset within the partition.
     * Monotonically increasing, never reused.
     */
    @Column(name = "offset_value", nullable = false)
    private Long offsetValue;

    /**
     * Optional message key.
     * Used for:
     * - Partitioning (hash(key) % numPartitions)
     * - Log compaction (keep latest per key)
     */
    @Column(name = "key")
    private byte[] key;

    /**
     * Message payload.
     */
    @Column(name = "value")
    private byte[] value;

    /**
     * Optional headers as JSON array.
     * Format: [{"key": "traceId", "value": "abc123"}]
     */
    @Column(name = "headers", columnDefinition = "jsonb")
    @Builder.Default
    private String headers = "[]";

    /**
     * Message timestamp in milliseconds.
     */
    @Column(nullable = false)
    private Long timestamp;

    @Column(name = "timestamp_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TimestampType timestampType = TimestampType.CREATE_TIME;

    /**
     * Producer ID for idempotent producers.
     */
    @Column(name = "producer_id")
    private Long producerId;

    /**
     * Producer epoch for fencing old producers.
     */
    @Column(name = "producer_epoch")
    private Short producerEpoch;

    /**
     * Sequence number for deduplication.
     * Unique per producer per partition.
     */
    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    /**
     * Whether this record is part of a transaction.
     */
    @Column(name = "is_transactional")
    @Builder.Default
    private Boolean isTransactional = false;

    /**
     * Whether this is a control record (commit/abort marker).
     */
    @Column(name = "is_control_record")
    @Builder.Default
    private Boolean isControlRecord = false;

    @Column(name = "compressed_size")
    private Integer compressedSize;

    @Column(name = "uncompressed_size")
    private Integer uncompressedSize;

    @Column(name = "compression_type")
    @Enumerated(EnumType.STRING)
    private CompressionType compressionType;

    /**
     * CRC32 checksum for data integrity.
     */
    @Column(name = "crc32")
    private Long crc32;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum TimestampType {
        CREATE_TIME, // Set by producer
        LOG_APPEND_TIME // Set by broker
    }

    public enum CompressionType {
        NONE,
        GZIP,
        SNAPPY,
        LZ4,
        ZSTD
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Get topic-partition string.
     */
    public String getTopicPartition() {
        return topicName + "-" + partitionNumber;
    }

    /**
     * Get key as string (if UTF-8 encoded).
     */
    public String getKeyAsString() {
        return key != null ? new String(key) : null;
    }

    /**
     * Get value as string (if UTF-8 encoded).
     */
    public String getValueAsString() {
        return value != null ? new String(value) : null;
    }

    /**
     * Calculate message size in bytes.
     */
    public int getSize() {
        int size = 8; // offset
        size += 8; // timestamp
        size += 4; // key length
        size += (key != null ? key.length : 0);
        size += 4; // value length
        size += (value != null ? value.length : 0);
        size += (headers != null ? headers.length() : 0);
        return size;
    }
}
