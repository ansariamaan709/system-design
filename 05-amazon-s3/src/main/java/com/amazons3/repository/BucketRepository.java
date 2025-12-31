package com.amazons3.repository;

import com.amazons3.entity.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Bucket entity operations.
 */
@Repository
public interface BucketRepository extends JpaRepository<Bucket, Long> {

    /**
     * Find bucket by name
     */
    Optional<Bucket> findByBucketName(String bucketName);

    /**
     * Check if bucket exists by name
     */
    boolean existsByBucketName(String bucketName);

    /**
     * Find all buckets owned by an account
     */
    List<Bucket> findByOwnerAccountId(Long ownerAccountId);

    /**
     * Count buckets owned by an account
     */
    long countByOwnerAccountId(Long ownerAccountId);

    /**
     * Update bucket statistics
     */
    @Modifying
    @Query("UPDATE Bucket b SET b.objectCount = b.objectCount + :countDelta, " +
            "b.totalSizeBytes = b.totalSizeBytes + :sizeDelta WHERE b.bucketId = :bucketId")
    void updateStats(Long bucketId, Long countDelta, Long sizeDelta);

    /**
     * Update versioning status
     */
    @Modifying
    @Query("UPDATE Bucket b SET b.versioningStatus = :status WHERE b.bucketId = :bucketId")
    void updateVersioningStatus(Long bucketId, Bucket.VersioningStatus status);

    /**
     * Find buckets by region
     */
    List<Bucket> findByRegion(String region);
}
