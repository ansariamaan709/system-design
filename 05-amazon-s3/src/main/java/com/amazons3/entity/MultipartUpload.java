package com.amazons3.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * MultipartUpload entity - tracks in-progress multipart uploads.
 */
@Entity
@Table(name = "multipart_uploads", indexes = {
        @Index(name = "idx_multipart_bucket_key", columnList = "bucket_id, object_key"),
        @Index(name = "idx_multipart_status", columnList = "status"),
        @Index(name = "idx_multipart_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultipartUpload {

    @Id
    @Column(name = "upload_id", length = 64)
    private String uploadId;

    @Column(name = "bucket_id", nullable = false)
    private Long bucketId;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "initiator_account_id", nullable = false)
    private Long initiatorAccountId;

    // Content info
    @Column(name = "content_type")
    @Builder.Default
    private String contentType = "application/octet-stream";

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_class", length = 30)
    @Builder.Default
    private Bucket.StorageClass storageClass = Bucket.StorageClass.STANDARD;

    // Encryption
    @Enumerated(EnumType.STRING)
    @Column(name = "sse_algorithm", length = 20)
    private S3Object.SseAlgorithm sseAlgorithm;

    @Column(name = "kms_key_id")
    private String kmsKeyId;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private UploadStatus status = UploadStatus.IN_PROGRESS;

    // Timestamps
    @Column(name = "initiated_at")
    @Builder.Default
    private Instant initiatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Check if upload has expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if upload is still in progress
     */
    public boolean isInProgress() {
        return status == UploadStatus.IN_PROGRESS && !isExpired();
    }

    public enum UploadStatus {
        IN_PROGRESS,
        COMPLETED,
        ABORTED
    }
}
