package com.amazons3.repository;

import com.amazons3.entity.S3Object;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for S3Object entity operations.
 * Optimized queries for high-throughput object storage operations.
 */
@Repository
public interface ObjectRepository extends JpaRepository<S3Object, Long> {

    /**
     * Find the latest version of an object by bucket and key
     */
    @Query("SELECT o FROM S3Object o WHERE o.bucketId = :bucketId AND o.objectKey = :key AND o.isLatest = true")
    Optional<S3Object> findLatestByBucketAndKey(Long bucketId, String key);

    /**
     * Find specific version of an object
     */
    Optional<S3Object> findByBucketIdAndObjectKeyAndVersionId(Long bucketId, String key, String versionId);

    /**
     * Find all versions of an object
     */
    @Query("SELECT o FROM S3Object o WHERE o.bucketId = :bucketId AND o.objectKey = :key " +
            "ORDER BY o.lastModified DESC")
    List<S3Object> findAllVersions(Long bucketId, String key);

    /**
     * List objects in bucket with prefix (for ListObjectsV2)
     */
    @Query("SELECT o FROM S3Object o WHERE o.bucketId = :bucketId AND o.isLatest = true " +
            "AND (:prefix IS NULL OR o.objectKey LIKE CONCAT(:prefix, '%')) " +
            "ORDER BY o.objectKey")
    Page<S3Object> listObjects(Long bucketId, String prefix, Pageable pageable);

    /**
     * List objects with start-after marker
     */
    @Query("SELECT o FROM S3Object o WHERE o.bucketId = :bucketId AND o.isLatest = true " +
            "AND (:prefix IS NULL OR o.objectKey LIKE CONCAT(:prefix, '%')) " +
            "AND (:startAfter IS NULL OR o.objectKey > :startAfter) " +
            "ORDER BY o.objectKey")
    Page<S3Object> listObjectsAfter(Long bucketId, String prefix, String startAfter, Pageable pageable);

    /**
     * List object versions
     */
    @Query("SELECT o FROM S3Object o WHERE o.bucketId = :bucketId " +
            "AND (:prefix IS NULL OR o.objectKey LIKE CONCAT(:prefix, '%')) " +
            "ORDER BY o.objectKey, o.lastModified DESC")
    Page<S3Object> listObjectVersions(Long bucketId, String prefix, Pageable pageable);

    /**
     * Check if object exists
     */
    @Query("SELECT COUNT(o) > 0 FROM S3Object o WHERE o.bucketId = :bucketId AND o.objectKey = :key AND o.isLatest = true")
    boolean existsByBucketAndKey(Long bucketId, String key);

    /**
     * Count objects in bucket
     */
    @Query("SELECT COUNT(o) FROM S3Object o WHERE o.bucketId = :bucketId AND o.isLatest = true")
    long countByBucket(Long bucketId);

    /**
     * Find expired objects
     */
    @Query("SELECT o FROM S3Object o WHERE o.expiresAt IS NOT NULL AND o.expiresAt < :now")
    List<S3Object> findExpiredObjects(Instant now);

    /**
     * Find objects to transition by storage class
     */
    @Query("SELECT o FROM S3Object o WHERE o.bucketId = :bucketId " +
            "AND o.storageClass = :currentClass " +
            "AND o.lastModified < :threshold")
    List<S3Object> findObjectsForTransition(Long bucketId,
            com.amazons3.entity.Bucket.StorageClass currentClass,
            Instant threshold);

    /**
     * Mark old versions as not latest
     */
    @Modifying
    @Query("UPDATE S3Object o SET o.isLatest = false WHERE o.bucketId = :bucketId " +
            "AND o.objectKey = :key AND o.versionId != :newVersionId")
    void markOldVersionsNotLatest(Long bucketId, String key, String newVersionId);

    /**
     * Delete all versions of an object
     */
    @Modifying
    @Query("DELETE FROM S3Object o WHERE o.bucketId = :bucketId AND o.objectKey = :key")
    void deleteAllVersions(Long bucketId, String key);

    /**
     * Find objects by storage path (for garbage collection)
     */
    List<S3Object> findByStoragePath(String storagePath);

    /**
     * Get common prefixes for delimiter-based listing
     */
    @Query(value = "SELECT DISTINCT " +
            "SUBSTRING(object_key FROM LENGTH(:prefix) + 1 FOR " +
            "POSITION(:delimiter IN SUBSTRING(object_key FROM LENGTH(:prefix) + 1)) - 1) || :delimiter " +
            "FROM objects WHERE bucket_id = :bucketId AND is_latest = true " +
            "AND object_key LIKE CONCAT(:prefix, '%') " +
            "AND POSITION(:delimiter IN SUBSTRING(object_key FROM LENGTH(:prefix) + 1)) > 0", nativeQuery = true)
    List<String> findCommonPrefixes(Long bucketId, String prefix, String delimiter);
}
