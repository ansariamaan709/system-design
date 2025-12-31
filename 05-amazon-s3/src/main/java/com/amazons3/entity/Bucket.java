package com.amazons3.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Bucket entity - container for S3 objects.
 * Supports versioning, object lock, and various configurations.
 */
@Entity
@Table(name = "buckets", indexes = {
        @Index(name = "idx_buckets_owner", columnList = "owner_account_id"),
        @Index(name = "idx_buckets_name", columnList = "bucket_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bucket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bucket_id")
    private Long bucketId;

    @Column(name = "bucket_name", unique = true, nullable = false, length = 63)
    private String bucketName;

    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    @Column(name = "region", length = 50)
    @Builder.Default
    private String region = "us-east-1";

    // Versioning
    @Enumerated(EnumType.STRING)
    @Column(name = "versioning_status", length = 20)
    @Builder.Default
    private VersioningStatus versioningStatus = VersioningStatus.DISABLED;

    // Object Lock (WORM)
    @Column(name = "object_lock_enabled")
    @Builder.Default
    private Boolean objectLockEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_retention_mode", length = 20)
    private RetentionMode defaultRetentionMode;

    @Column(name = "default_retention_days")
    private Integer defaultRetentionDays;

    // Storage class
    @Enumerated(EnumType.STRING)
    @Column(name = "default_storage_class", length = 30)
    @Builder.Default
    private StorageClass defaultStorageClass = StorageClass.STANDARD;

    // Statistics
    @Column(name = "object_count")
    @Builder.Default
    private Long objectCount = 0L;

    @Column(name = "total_size_bytes")
    @Builder.Default
    private Long totalSizeBytes = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Check if versioning is enabled
     */
    public boolean isVersioningEnabled() {
        return versioningStatus == VersioningStatus.ENABLED;
    }

    /**
     * Check if bucket allows new versions
     */
    public boolean allowsVersioning() {
        return versioningStatus != VersioningStatus.DISABLED;
    }

    public enum VersioningStatus {
        DISABLED, // Versioning never enabled
        ENABLED, // Versioning active
        SUSPENDED // Versioning was enabled, now suspended
    }

    public enum RetentionMode {
        GOVERNANCE, // Can be overridden with special permissions
        COMPLIANCE // Cannot be overridden
    }

    public enum StorageClass {
        STANDARD, // Frequent access
        STANDARD_IA, // Infrequent access
        ONEZONE_IA, // Single AZ, infrequent
        INTELLIGENT_TIERING, // Auto-tiering
        GLACIER, // Archive
        GLACIER_IR, // Glacier Instant Retrieval
        DEEP_ARCHIVE // Long-term archive
    }
}
