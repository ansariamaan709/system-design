package com.amazons3.repository;

import com.amazons3.entity.MultipartUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for MultipartUpload entity operations.
 */
@Repository
public interface MultipartUploadRepository extends JpaRepository<MultipartUpload, String> {

    /**
     * Find active upload by ID
     */
    @Query("SELECT m FROM MultipartUpload m WHERE m.uploadId = :uploadId AND m.status = 'IN_PROGRESS'")
    Optional<MultipartUpload> findActiveUpload(String uploadId);

    /**
     * List uploads for a bucket
     */
    List<MultipartUpload> findByBucketIdAndStatus(Long bucketId, MultipartUpload.UploadStatus status);

    /**
     * List uploads for bucket and key prefix
     */
    @Query("SELECT m FROM MultipartUpload m WHERE m.bucketId = :bucketId " +
            "AND m.status = 'IN_PROGRESS' " +
            "AND (:prefix IS NULL OR m.objectKey LIKE CONCAT(:prefix, '%'))")
    List<MultipartUpload> findByBucketAndPrefix(Long bucketId, String prefix);

    /**
     * Find expired uploads
     */
    @Query("SELECT m FROM MultipartUpload m WHERE m.status = 'IN_PROGRESS' AND m.expiresAt < :now")
    List<MultipartUpload> findExpiredUploads(Instant now);

    /**
     * Update upload status
     */
    @Modifying
    @Query("UPDATE MultipartUpload m SET m.status = :status, m.completedAt = :completedAt " +
            "WHERE m.uploadId = :uploadId")
    void updateStatus(String uploadId, MultipartUpload.UploadStatus status, Instant completedAt);

    /**
     * Count active uploads for account
     */
    @Query("SELECT COUNT(m) FROM MultipartUpload m WHERE m.initiatorAccountId = :accountId " +
            "AND m.status = 'IN_PROGRESS'")
    long countActiveByAccount(Long accountId);
}
