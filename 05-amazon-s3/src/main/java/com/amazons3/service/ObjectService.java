package com.amazons3.service;

import com.amazons3.dto.ListObjectsV2Response;
import com.amazons3.dto.ObjectDto;
import com.amazons3.entity.Bucket;
import com.amazons3.entity.ObjectMetadata;
import com.amazons3.entity.S3Object;
import com.amazons3.exception.S3Exception;
import com.amazons3.repository.BucketRepository;
import com.amazons3.repository.ObjectMetadataRepository;
import com.amazons3.repository.ObjectRepository;
import com.amazons3.storage.StorageEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

/**
 * Service for object storage operations.
 * Handles PUT, GET, DELETE, COPY, and listing operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectService {

    private final ObjectRepository objectRepository;
    private final ObjectMetadataRepository metadataRepository;
    private final BucketRepository bucketRepository;
    private final BucketService bucketService;
    private final StorageEngine storageEngine;

    private static final int DEFAULT_MAX_KEYS = 1000;

    /**
     * Put an object into a bucket.
     */
    @Transactional
    @CacheEvict(value = "objects", key = "#bucketName + ':' + #key")
    public S3Object putObject(String bucketName, String key, InputStream data,
            String contentType, Map<String, String> userMetadata) throws IOException {

        log.debug("[OBJECT] PUT {}/{}", bucketName, key);
        long startTime = System.currentTimeMillis();

        Bucket bucket = bucketService.getBucket(bucketName);

        // Store the data
        StorageEngine.StorageResult storageResult = storageEngine.store(data);

        // Generate version ID if versioning enabled
        String versionId = bucket.isVersioningEnabled()
                ? UUID.randomUUID().toString().replace("-", "")
                : "null";

        // Mark old version as not latest
        if (bucket.isVersioningEnabled()) {
            objectRepository.markOldVersionsNotLatest(bucket.getBucketId(), key, versionId);
        } else {
            // Delete existing object if versioning is disabled
            objectRepository.findLatestByBucketAndKey(bucket.getBucketId(), key)
                    .ifPresent(existing -> {
                        metadataRepository.deleteByObjectId(existing.getObjectId());
                        objectRepository.delete(existing);
                    });
        }

        // Create object entity
        S3Object s3Object = S3Object.builder()
                .bucketId(bucket.getBucketId())
                .objectKey(key)
                .versionId(versionId)
                .isLatest(true)
                .etag(storageResult.getEtag())
                .sizeBytes(storageResult.getSizeBytes())
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .storagePath(storageResult.getStoragePath())
                .checksumSha256(storageResult.getChecksumSha256())
                .storageClass(bucket.getDefaultStorageClass())
                .lastModified(Instant.now())
                .build();

        s3Object = objectRepository.save(s3Object);

        // Store user metadata
        if (userMetadata != null && !userMetadata.isEmpty()) {
            for (Map.Entry<String, String> entry : userMetadata.entrySet()) {
                ObjectMetadata metadata = ObjectMetadata.builder()
                        .objectId(s3Object.getObjectId())
                        .metaKey(entry.getKey())
                        .metaValue(entry.getValue())
                        .build();
                metadataRepository.save(metadata);
            }
        }

        // Update bucket stats (trigger handles this, but we can be explicit)
        bucketRepository.updateStats(bucket.getBucketId(), 1L, storageResult.getSizeBytes());

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[OBJECT] PUT {}/{} - {} bytes, ETag: {} ({}ms)",
                bucketName, key, storageResult.getSizeBytes(), storageResult.getEtag(), elapsed);

        return s3Object;
    }

    /**
     * Get an object from a bucket.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "objects", key = "#bucketName + ':' + #key")
    public S3Object getObject(String bucketName, String key, String versionId) {
        Bucket bucket = bucketService.getBucket(bucketName);

        S3Object object;
        if (versionId != null && !"null".equals(versionId)) {
            object = objectRepository.findByBucketIdAndObjectKeyAndVersionId(
                    bucket.getBucketId(), key, versionId)
                    .orElseThrow(() -> S3Exception.noSuchKey(key));
        } else {
            object = objectRepository.findLatestByBucketAndKey(bucket.getBucketId(), key)
                    .orElseThrow(() -> S3Exception.noSuchKey(key));
        }

        // Check for delete marker
        if (object.getIsDeleteMarker()) {
            throw S3Exception.noSuchKey(key);
        }

        return object;
    }

    /**
     * Get object data as InputStream.
     */
    public InputStream getObjectData(S3Object object) throws IOException {
        return storageEngine.retrieve(object.getStoragePath());
    }

    /**
     * Get object data with byte range.
     */
    public InputStream getObjectDataRange(S3Object object, long start, long end) throws IOException {
        return storageEngine.retrieveRange(object.getStoragePath(), start, end);
    }

    /**
     * Get object metadata (HEAD operation).
     */
    @Transactional(readOnly = true)
    public S3Object headObject(String bucketName, String key, String versionId) {
        return getObject(bucketName, key, versionId);
    }

    /**
     * Delete an object.
     */
    @Transactional
    @CacheEvict(value = "objects", key = "#bucketName + ':' + #key")
    public DeleteResult deleteObject(String bucketName, String key, String versionId) {
        log.debug("[OBJECT] DELETE {}/{}", bucketName, key);

        Bucket bucket = bucketService.getBucket(bucketName);

        if (bucket.isVersioningEnabled()) {
            if (versionId != null && !"null".equals(versionId)) {
                // Delete specific version
                S3Object object = objectRepository.findByBucketIdAndObjectKeyAndVersionId(
                        bucket.getBucketId(), key, versionId)
                        .orElseThrow(() -> S3Exception.noSuchKey(key));

                // Check object lock
                if (!object.isDeletable()) {
                    throw S3Exception.accessDenied("Object is locked");
                }

                objectRepository.delete(object);
                bucketRepository.updateStats(bucket.getBucketId(), -1L, -object.getSizeBytes());

                return new DeleteResult(true, versionId, false);
            } else {
                // Create delete marker
                String deleteMarkerVersionId = UUID.randomUUID().toString().replace("-", "");

                // Mark current version as not latest
                objectRepository.markOldVersionsNotLatest(bucket.getBucketId(), key, deleteMarkerVersionId);

                // Create delete marker
                S3Object deleteMarker = S3Object.builder()
                        .bucketId(bucket.getBucketId())
                        .objectKey(key)
                        .versionId(deleteMarkerVersionId)
                        .isLatest(true)
                        .isDeleteMarker(true)
                        .etag("")
                        .sizeBytes(0L)
                        .storagePath("")
                        .lastModified(Instant.now())
                        .build();

                objectRepository.save(deleteMarker);

                return new DeleteResult(false, deleteMarkerVersionId, true);
            }
        } else {
            // Non-versioned delete
            S3Object object = objectRepository.findLatestByBucketAndKey(bucket.getBucketId(), key)
                    .orElseThrow(() -> S3Exception.noSuchKey(key));

            // Check object lock
            if (!object.isDeletable()) {
                throw S3Exception.accessDenied("Object is locked");
            }

            metadataRepository.deleteByObjectId(object.getObjectId());
            objectRepository.delete(object);
            bucketRepository.updateStats(bucket.getBucketId(), -1L, -object.getSizeBytes());

            // Note: Physical storage deletion handled by garbage collection

            return new DeleteResult(true, null, false);
        }
    }

    /**
     * Copy an object.
     */
    @Transactional
    public S3Object copyObject(String sourceBucket, String sourceKey, String sourceVersionId,
            String destBucket, String destKey, Map<String, String> newMetadata) {

        log.debug("[OBJECT] COPY {}/{} -> {}/{}", sourceBucket, sourceKey, destBucket, destKey);

        // Get source object
        S3Object source = getObject(sourceBucket, sourceKey, sourceVersionId);

        Bucket destBucketEntity = bucketService.getBucket(destBucket);

        // Copy storage path (content-addressable, so no physical copy needed)
        String storagePath = storageEngine.copy(source.getStoragePath());

        // Generate version ID if versioning enabled
        String versionId = destBucketEntity.isVersioningEnabled()
                ? UUID.randomUUID().toString().replace("-", "")
                : "null";

        // Mark old version as not latest
        if (destBucketEntity.isVersioningEnabled()) {
            objectRepository.markOldVersionsNotLatest(destBucketEntity.getBucketId(), destKey, versionId);
        } else {
            // Delete existing object
            objectRepository.findLatestByBucketAndKey(destBucketEntity.getBucketId(), destKey)
                    .ifPresent(existing -> {
                        metadataRepository.deleteByObjectId(existing.getObjectId());
                        objectRepository.delete(existing);
                    });
        }

        // Create new object
        S3Object copy = S3Object.builder()
                .bucketId(destBucketEntity.getBucketId())
                .objectKey(destKey)
                .versionId(versionId)
                .isLatest(true)
                .etag(source.getEtag())
                .sizeBytes(source.getSizeBytes())
                .contentType(source.getContentType())
                .storagePath(storagePath)
                .checksumSha256(source.getChecksumSha256())
                .storageClass(destBucketEntity.getDefaultStorageClass())
                .lastModified(Instant.now())
                .build();

        copy = objectRepository.save(copy);

        // Copy or replace metadata
        if (newMetadata != null) {
            for (Map.Entry<String, String> entry : newMetadata.entrySet()) {
                ObjectMetadata metadata = ObjectMetadata.builder()
                        .objectId(copy.getObjectId())
                        .metaKey(entry.getKey())
                        .metaValue(entry.getValue())
                        .build();
                metadataRepository.save(metadata);
            }
        } else {
            // Copy source metadata
            List<ObjectMetadata> sourceMetadata = metadataRepository.findByObjectId(source.getObjectId());
            for (ObjectMetadata meta : sourceMetadata) {
                ObjectMetadata copyMeta = ObjectMetadata.builder()
                        .objectId(copy.getObjectId())
                        .metaKey(meta.getMetaKey())
                        .metaValue(meta.getMetaValue())
                        .build();
                metadataRepository.save(copyMeta);
            }
        }

        // Update bucket stats
        bucketRepository.updateStats(destBucketEntity.getBucketId(), 1L, source.getSizeBytes());

        log.info("[OBJECT] COPY {}/{} -> {}/{} (version: {})",
                sourceBucket, sourceKey, destBucket, destKey, versionId);

        return copy;
    }

    /**
     * List objects in a bucket (ListObjectsV2).
     */
    @Transactional(readOnly = true)
    public ListObjectsV2Response listObjects(String bucketName, String prefix, String delimiter,
            String startAfter, String continuationToken, int maxKeys) {

        Bucket bucket = bucketService.getBucket(bucketName);

        maxKeys = Math.min(maxKeys > 0 ? maxKeys : DEFAULT_MAX_KEYS, DEFAULT_MAX_KEYS);

        // Determine start position
        String effectiveStartAfter = continuationToken != null ? continuationToken : startAfter;

        // Query objects
        Page<S3Object> page;
        if (effectiveStartAfter != null) {
            page = objectRepository.listObjectsAfter(bucket.getBucketId(), prefix, effectiveStartAfter,
                    PageRequest.of(0, maxKeys + 1));
        } else {
            page = objectRepository.listObjects(bucket.getBucketId(), prefix,
                    PageRequest.of(0, maxKeys + 1));
        }

        List<S3Object> objects = page.getContent();
        boolean isTruncated = objects.size() > maxKeys;

        if (isTruncated) {
            objects = objects.subList(0, maxKeys);
        }

        // Build response
        ListObjectsV2Response.ListObjectsV2ResponseBuilder responseBuilder = ListObjectsV2Response.builder()
                .name(bucketName)
                .prefix(prefix)
                .delimiter(delimiter)
                .maxKeys(maxKeys)
                .keyCount(objects.size())
                .isTruncated(isTruncated);

        if (startAfter != null) {
            responseBuilder.startAfter(startAfter);
        }

        if (continuationToken != null) {
            responseBuilder.continuationToken(continuationToken);
        }

        if (isTruncated && !objects.isEmpty()) {
            responseBuilder.nextContinuationToken(objects.get(objects.size() - 1).getObjectKey());
        }

        // Handle delimiter (common prefixes)
        if (delimiter != null && !delimiter.isEmpty()) {
            List<String> commonPrefixes = objectRepository.findCommonPrefixes(
                    bucket.getBucketId(), prefix != null ? prefix : "", delimiter);

            responseBuilder.commonPrefixes(commonPrefixes.stream()
                    .map(ListObjectsV2Response.CommonPrefix::new)
                    .toList());

            // Filter out objects that match common prefixes
            String finalPrefix = prefix != null ? prefix : "";
            objects = objects.stream()
                    .filter(obj -> {
                        String keyAfterPrefix = obj.getObjectKey().substring(finalPrefix.length());
                        return !keyAfterPrefix.contains(delimiter);
                    })
                    .toList();
        }

        // Convert to DTOs
        List<ObjectDto> contents = objects.stream()
                .map(ObjectDto::fromEntity)
                .toList();

        responseBuilder.contents(contents);

        return responseBuilder.build();
    }

    /**
     * Get object user metadata.
     */
    public Map<String, String> getObjectMetadata(Long objectId) {
        List<ObjectMetadata> metadata = metadataRepository.findByObjectId(objectId);
        Map<String, String> result = new HashMap<>();
        for (ObjectMetadata meta : metadata) {
            result.put(meta.getMetaKey(), meta.getMetaValue());
        }
        return result;
    }

    /**
     * Check if object exists.
     */
    public boolean objectExists(String bucketName, String key) {
        Bucket bucket = bucketService.getBucket(bucketName);
        return objectRepository.existsByBucketAndKey(bucket.getBucketId(), key);
    }

    /**
     * Result of delete operation.
     */
    public record DeleteResult(boolean deleted, String versionId, boolean deleteMarker) {
    }
}
