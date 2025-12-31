package com.amazons3.repository;

import com.amazons3.entity.MultipartPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MultipartPart entity operations.
 */
@Repository
public interface MultipartPartRepository extends JpaRepository<MultipartPart, Long> {

    /**
     * Find all parts for an upload
     */
    List<MultipartPart> findByUploadIdOrderByPartNumber(String uploadId);

    /**
     * Find specific part
     */
    Optional<MultipartPart> findByUploadIdAndPartNumber(String uploadId, Integer partNumber);

    /**
     * Count parts for an upload
     */
    long countByUploadId(String uploadId);

    /**
     * Get total size of all parts
     */
    @Query("SELECT COALESCE(SUM(p.sizeBytes), 0) FROM MultipartPart p WHERE p.uploadId = :uploadId")
    Long getTotalSize(String uploadId);

    /**
     * Delete all parts for an upload
     */
    void deleteByUploadId(String uploadId);

    /**
     * Check if part exists
     */
    boolean existsByUploadIdAndPartNumber(String uploadId, Integer partNumber);
}
