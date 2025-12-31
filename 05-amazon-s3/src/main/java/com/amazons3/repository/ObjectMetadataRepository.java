package com.amazons3.repository;

import com.amazons3.entity.ObjectMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ObjectMetadata entity operations.
 */
@Repository
public interface ObjectMetadataRepository extends JpaRepository<ObjectMetadata, Long> {

    /**
     * Find all metadata for an object
     */
    List<ObjectMetadata> findByObjectId(Long objectId);

    /**
     * Find specific metadata entry
     */
    Optional<ObjectMetadata> findByObjectIdAndMetaKey(Long objectId, String metaKey);

    /**
     * Delete all metadata for an object
     */
    void deleteByObjectId(Long objectId);
}
