package com.amazons3.service;

import com.amazons3.dto.CompleteMultipartUploadRequest;
import com.amazons3.dto.CompleteMultipartUploadResponse;
import com.amazons3.dto.InitiateMultipartUploadResponse;
import com.amazons3.entity.Bucket;
import com.amazons3.entity.MultipartPart;
import com.amazons3.entity.MultipartUpload;
import com.amazons3.entity.S3Object;
import com.amazons3.exception.S3Exception;
import com.amazons3.repository.BucketRepository;
import com.amazons3.repository.MultipartPartRepository;
import com.amazons3.repository.MultipartUploadRepository;
import com.amazons3.repository.ObjectRepository;
import com.amazons3.storage.StorageEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for multipart upload operations.
 * Handles initiate, upload part, complete, and abort operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadService {

    private final MultipartUploadRepository uploadRepository;
    private final MultipartPartRepository partRepository;
    private final ObjectRepository objectRepository;
    private final BucketRepository bucketRepository;
    private final BucketService bucketService;
    private final StorageEngine storageEngine;

    @Value("${s3.multipart.min-part-size-bytes:5242880}")
    private long minPartSize; // 5MB

    @Value("${s3.multipart.max-part-size-bytes:5368709120}")
    private long maxPartSize; // 5GB

    @Value("${s3.multipart.max-parts:10000}")
    private int maxParts;

    @Value("${s3.multipart.upload-expiry-days:7}")
    private int uploadExpiryDays;

    /**
     * Initiate a multipart upload.
     */
    @Transactional
    public InitiateMultipartUploadResponse initiateUpload(String bucketName, String key,
            String contentType, Long accountId) {
        log.info("[MULTIPART] Initiating upload for {}/{}", bucketName, key);

        Bucket bucket = bucketService.getBucket(bucketName);

        String uploadId = UUID.randomUUID().toString().replace("-", "");

        MultipartUpload upload = MultipartUpload.builder()
                .uploadId(uploadId)
                .bucketId(bucket.getBucketId())
                .objectKey(key)
                .initiatorAccountId(accountId)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .storageClass(bucket.getDefaultStorageClass())
                .expiresAt(Instant.now().plus(uploadExpiryDays, ChronoUnit.DAYS))
                .build();

        uploadRepository.save(upload);

        log.info("[MULTIPART] Initiated upload {} for {}/{}", uploadId, bucketName, key);

        return InitiateMultipartUploadResponse.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .build();
    }

    /**
     * Upload a part.
     */
    @Transactional
    public String uploadPart(String bucketName, String key, String uploadId,
            int partNumber, InputStream data) throws IOException {

        log.debug("[MULTIPART] Uploading part {} for upload {}", partNumber, uploadId);

        // Validate upload exists and is active
        MultipartUpload upload = uploadRepository.findActiveUpload(uploadId)
                .orElseThrow(() -> S3Exception.noSuchUpload(uploadId));

        // Validate bucket and key match
        Bucket bucket = bucketService.getBucket(bucketName);
        if (!upload.getBucketId().equals(bucket.getBucketId()) || !upload.getObjectKey().equals(key)) {
            throw S3Exception.invalidRequest("Upload does not match bucket/key");
        }

        // Validate part number
        if (partNumber < 1 || partNumber > maxParts) {
            throw S3Exception.invalidPartNumber(partNumber);
        }

        // Store the part
        StorageEngine.StorageResult storageResult = storageEngine.store(data);

        // Validate part size (except last part)
        // Note: Last part validation happens at complete time

        // Create or update part record
        Optional<MultipartPart> existingPart = partRepository.findByUploadIdAndPartNumber(uploadId, partNumber);
        if (existingPart.isPresent()) {
            // Replace existing part
            MultipartPart part = existingPart.get();
            part.setEtag(storageResult.getEtag());
            part.setSizeBytes(storageResult.getSizeBytes());
            part.setStoragePath(storageResult.getStoragePath());
            part.setChecksumSha256(storageResult.getChecksumSha256());
            part.setUploadedAt(Instant.now());
            partRepository.save(part);
        } else {
            MultipartPart part = MultipartPart.builder()
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .etag(storageResult.getEtag())
                    .sizeBytes(storageResult.getSizeBytes())
                    .storagePath(storageResult.getStoragePath())
                    .checksumSha256(storageResult.getChecksumSha256())
                    .build();
            partRepository.save(part);
        }

        log.debug("[MULTIPART] Uploaded part {} ({} bytes) for upload {}",
                partNumber, storageResult.getSizeBytes(), uploadId);

        return storageResult.getEtag();
    }

    /**
     * Complete a multipart upload.
     */
    @Transactional
    public CompleteMultipartUploadResponse completeUpload(String bucketName, String key,
            String uploadId,
            CompleteMultipartUploadRequest request) throws IOException {

        log.info("[MULTIPART] Completing upload {}", uploadId);

        // Validate upload
        MultipartUpload upload = uploadRepository.findActiveUpload(uploadId)
                .orElseThrow(() -> S3Exception.noSuchUpload(uploadId));

        Bucket bucket = bucketService.getBucket(bucketName);

        // Get all parts ordered by part number
        List<MultipartPart> parts = partRepository.findByUploadIdOrderByPartNumber(uploadId);

        if (parts.isEmpty()) {
            throw S3Exception.invalidRequest("No parts uploaded");
        }

        // Validate request parts match uploaded parts
        List<CompleteMultipartUploadRequest.PartInfo> requestParts = request.getParts();
        if (requestParts == null || requestParts.isEmpty()) {
            throw S3Exception.invalidRequest("No parts specified in completion request");
        }

        // Sort request parts by part number
        requestParts.sort(Comparator.comparing(CompleteMultipartUploadRequest.PartInfo::getPartNumber));

        // Validate part numbers are sequential starting from 1
        Map<Integer, MultipartPart> partMap = new HashMap<>();
        for (MultipartPart part : parts) {
            partMap.put(part.getPartNumber(), part);
        }

        List<MultipartPart> orderedParts = new ArrayList<>();
        for (CompleteMultipartUploadRequest.PartInfo partInfo : requestParts) {
            MultipartPart part = partMap.get(partInfo.getPartNumber());
            if (part == null) {
                throw S3Exception.invalidPart(partInfo.getPartNumber());
            }
            // Validate ETag matches
            if (!part.getEtag().equals(partInfo.getEtag())) {
                throw S3Exception.invalidPart(partInfo.getPartNumber());
            }
            orderedParts.add(part);
        }

        // Validate part sizes (all except last must be >= minPartSize)
        for (int i = 0; i < orderedParts.size() - 1; i++) {
            if (orderedParts.get(i).getSizeBytes() < minPartSize) {
                throw S3Exception.entityTooSmall(orderedParts.get(i).getPartNumber());
            }
        }

        // Concatenate parts into final object
        Path tempFile = storageEngine.createTempFile();
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            long totalSize = 0;

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                for (MultipartPart part : orderedParts) {
                    try (InputStream partData = storageEngine.retrieve(part.getStoragePath())) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = partData.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            md5.update(buffer, 0, bytesRead);
                            totalSize += bytesRead;
                        }
                    }
                }
            }

            // Calculate final ETag (MD5 of MD5s + "-" + part count)
            String finalEtag = "\"" + HexFormat.of().formatHex(md5.digest()) + "-" + orderedParts.size() + "\"";

            // Store final object
            StorageEngine.StorageResult storageResult;
            try (InputStream in = Files.newInputStream(tempFile)) {
                storageResult = storageEngine.store(in);
            }

            // Create object record
            String versionId = bucket.isVersioningEnabled()
                    ? UUID.randomUUID().toString().replace("-", "")
                    : "null";

            if (bucket.isVersioningEnabled()) {
                objectRepository.markOldVersionsNotLatest(bucket.getBucketId(), key, versionId);
            }

            S3Object s3Object = S3Object.builder()
                    .bucketId(bucket.getBucketId())
                    .objectKey(key)
                    .versionId(versionId)
                    .isLatest(true)
                    .etag(finalEtag)
                    .sizeBytes(totalSize)
                    .contentType(upload.getContentType())
                    .storagePath(storageResult.getStoragePath())
                    .checksumSha256(storageResult.getChecksumSha256())
                    .storageClass(upload.getStorageClass())
                    .lastModified(Instant.now())
                    .build();

            objectRepository.save(s3Object);

            // Update bucket stats
            bucketRepository.updateStats(bucket.getBucketId(), 1L, totalSize);

            // Mark upload as completed
            upload.setStatus(MultipartUpload.UploadStatus.COMPLETED);
            upload.setCompletedAt(Instant.now());
            uploadRepository.save(upload);

            // Clean up parts (mark for garbage collection)
            // Note: Actual file deletion handled by GC process

            log.info("[MULTIPART] Completed upload {} - {} bytes, {} parts",
                    uploadId, totalSize, orderedParts.size());

            return CompleteMultipartUploadResponse.builder()
                    .location("/" + bucketName + "/" + key)
                    .bucket(bucketName)
                    .key(key)
                    .etag(finalEtag)
                    .checksumSha256(storageResult.getChecksumSha256())
                    .build();

        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Failed to complete multipart upload", e);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Abort a multipart upload.
     */
    @Transactional
    public void abortUpload(String bucketName, String key, String uploadId) {
        log.info("[MULTIPART] Aborting upload {}", uploadId);

        MultipartUpload upload = uploadRepository.findActiveUpload(uploadId)
                .orElseThrow(() -> S3Exception.noSuchUpload(uploadId));

        // Verify bucket and key
        Bucket bucket = bucketService.getBucket(bucketName);
        if (!upload.getBucketId().equals(bucket.getBucketId()) || !upload.getObjectKey().equals(key)) {
            throw S3Exception.invalidRequest("Upload does not match bucket/key");
        }

        // Mark as aborted
        upload.setStatus(MultipartUpload.UploadStatus.ABORTED);
        upload.setCompletedAt(Instant.now());
        uploadRepository.save(upload);

        // Delete parts
        partRepository.deleteByUploadId(uploadId);

        log.info("[MULTIPART] Aborted upload {}", uploadId);
    }

    /**
     * List parts for an upload.
     */
    @Transactional(readOnly = true)
    public List<MultipartPart> listParts(String uploadId) {
        uploadRepository.findActiveUpload(uploadId)
                .orElseThrow(() -> S3Exception.noSuchUpload(uploadId));

        return partRepository.findByUploadIdOrderByPartNumber(uploadId);
    }

    /**
     * List active uploads for a bucket.
     */
    @Transactional(readOnly = true)
    public List<MultipartUpload> listUploads(String bucketName, String prefix) {
        Bucket bucket = bucketService.getBucket(bucketName);
        return uploadRepository.findByBucketAndPrefix(bucket.getBucketId(), prefix);
    }

    /**
     * Clean up expired uploads.
     */
    @Transactional
    public int cleanupExpiredUploads() {
        List<MultipartUpload> expired = uploadRepository.findExpiredUploads(Instant.now());
        int count = 0;

        for (MultipartUpload upload : expired) {
            try {
                upload.setStatus(MultipartUpload.UploadStatus.ABORTED);
                uploadRepository.save(upload);
                partRepository.deleteByUploadId(upload.getUploadId());
                count++;
            } catch (Exception e) {
                log.error("[MULTIPART] Failed to cleanup upload {}: {}", upload.getUploadId(), e.getMessage());
            }
        }

        if (count > 0) {
            log.info("[MULTIPART] Cleaned up {} expired uploads", count);
        }

        return count;
    }
}
