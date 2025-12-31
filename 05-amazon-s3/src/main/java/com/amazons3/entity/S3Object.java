package com.amazons3.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * S3Object entity - represents stored objects in buckets.
 * Supports versioning, storage classes, encryption, and object lock.
 */
@Entity
@Table(name = "objects", indexes = {
        @Index(name = "idx_objects_bucket_key", columnList = "bucket_id, object_key"),
        @Index(name = "idx_objects_latest", columnList = "bucket_id, is_latest"),
        @Index(name = "idx_objects_storage_path", columnList = "storage_path"),
        @Index(name = "idx_objects_expires", columnList = "expires_at")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { "bucket_id", "object_key", "version_id" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class S3Object {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "object_id")
    private Long objectId;

    @Column(name = "bucket_id", nullable = false)
    private Long bucketId;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    // Versioning
    @Column(name = "version_id", nullable = false, length = 64)
    @Builder.Default
    private String versionId = "null";

    @Column(name = "is_latest")
    @Builder.Default
    private Boolean isLatest = true;

    @Column(name = "is_delete_marker")
    @Builder.Default
    private Boolean isDeleteMarker = false;

    // Content
    @Column(name = "etag", nullable = false, length = 64)
    private String etag;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "content_type")
    @Builder.Default
    private String contentType = "application/octet-stream";

    @Column(name = "content_encoding", length = 50)
    private String contentEncoding;

    @Column(name = "content_disposition")
    private String contentDisposition;

    @Column(name = "content_language", length = 50)
    private String contentLanguage;

    @Column(name = "cache_control")
    private String cacheControl;

    // Storage
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_class", length = 30)
    @Builder.Default
    private Bucket.StorageClass storageClass = Bucket.StorageClass.STANDARD;

    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    // Checksums
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "checksum_crc32", length = 16)
    private String checksumCrc32;

    // Object Lock
    @Enumerated(EnumType.STRING)
    @Column(name = "lock_mode", length = 20)
    private Bucket.RetentionMode lockMode;

    @Column(name = "lock_retain_until")
    private Instant lockRetainUntil;

    @Column(name = "legal_hold")
    @Builder.Default
    private Boolean legalHold = false;

    // Encryption
    @Enumerated(EnumType.STRING)
    @Column(name = "sse_algorithm", length = 20)
    private SseAlgorithm sseAlgorithm;

    @Column(name = "kms_key_id")
    private String kmsKeyId;

    // Timestamps
    @Column(name = "last_modified")
    @Builder.Default
    private Instant lastModified = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Check if object is under retention lock
     */
    public boolean isLocked() {
        if (lockMode == null) {
            return false;
        }
        return lockRetainUntil != null && Instant.now().isBefore(lockRetainUntil);
    }

    /**
     * Check if object has legal hold
     */
    public boolean hasLegalHold() {
        return Boolean.TRUE.equals(legalHold);
    }

    /**
     * Check if object can be deleted
     */
    public boolean isDeletable() {
        return !isLocked() && !hasLegalHold();
    }

    /**
     * Check if object has expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public enum SseAlgorithm {
        AES256, // S3-managed keys
        AWS_KMS // KMS-managed keys
    }
}
