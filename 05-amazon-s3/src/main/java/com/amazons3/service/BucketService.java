package com.amazons3.service;

import com.amazons3.dto.BucketDto;
import com.amazons3.dto.ListBucketsResponse;
import com.amazons3.dto.OwnerDto;
import com.amazons3.entity.Account;
import com.amazons3.entity.Bucket;
import com.amazons3.exception.S3Exception;
import com.amazons3.repository.AccountRepository;
import com.amazons3.repository.BucketRepository;
import com.amazons3.repository.ObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for bucket management operations.
 * Handles bucket CRUD, versioning, and configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BucketService {

    private final BucketRepository bucketRepository;
    private final ObjectRepository objectRepository;
    private final AccountRepository accountRepository;

    // S3 bucket naming rules
    private static final Pattern BUCKET_NAME_PATTERN = Pattern.compile(
            "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");

    private static final int MAX_BUCKETS_PER_ACCOUNT = 100;

    /**
     * Create a new bucket.
     */
    @Transactional
    @CacheEvict(value = "buckets", key = "#bucketName")
    public Bucket createBucket(String bucketName, Long accountId, String region) {
        log.info("[BUCKET] Creating bucket: {} for account: {}", bucketName, accountId);

        // Validate bucket name
        validateBucketName(bucketName);

        // Check if bucket already exists (globally unique)
        if (bucketRepository.existsByBucketName(bucketName)) {
            throw S3Exception.bucketAlreadyExists(bucketName);
        }

        // Check account bucket limit
        long bucketCount = bucketRepository.countByOwnerAccountId(accountId);
        if (bucketCount >= MAX_BUCKETS_PER_ACCOUNT) {
            throw S3Exception.tooManyBuckets();
        }

        // Create bucket
        Bucket bucket = Bucket.builder()
                .bucketName(bucketName)
                .ownerAccountId(accountId)
                .region(region != null ? region : "us-east-1")
                .build();

        bucket = bucketRepository.save(bucket);
        log.info("[BUCKET] Created bucket: {} (id: {})", bucketName, bucket.getBucketId());

        return bucket;
    }

    /**
     * Delete a bucket.
     */
    @Transactional
    @CacheEvict(value = "buckets", key = "#bucketName")
    public void deleteBucket(String bucketName, Long accountId) {
        log.info("[BUCKET] Deleting bucket: {}", bucketName);

        Bucket bucket = getBucket(bucketName);

        // Verify ownership
        if (!bucket.getOwnerAccountId().equals(accountId)) {
            throw S3Exception.accessDenied();
        }

        // Check if bucket is empty
        long objectCount = objectRepository.countByBucket(bucket.getBucketId());
        if (objectCount > 0) {
            throw S3Exception.bucketNotEmpty(bucketName);
        }

        bucketRepository.delete(bucket);
        log.info("[BUCKET] Deleted bucket: {}", bucketName);
    }

    /**
     * Get bucket by name.
     */
    @Cacheable(value = "buckets", key = "#bucketName")
    public Bucket getBucket(String bucketName) {
        return bucketRepository.findByBucketName(bucketName)
                .orElseThrow(() -> S3Exception.noSuchBucket(bucketName));
    }

    /**
     * Get bucket if exists, otherwise return null.
     */
    public Bucket getBucketIfExists(String bucketName) {
        return bucketRepository.findByBucketName(bucketName).orElse(null);
    }

    /**
     * Check if bucket exists.
     */
    public boolean bucketExists(String bucketName) {
        return bucketRepository.existsByBucketName(bucketName);
    }

    /**
     * List all buckets for an account.
     */
    public ListBucketsResponse listBuckets(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> S3Exception.invalidAccessKeyId());

        List<Bucket> buckets = bucketRepository.findByOwnerAccountId(accountId);

        List<BucketDto> bucketDtos = buckets.stream()
                .map(BucketDto::fromEntity)
                .toList();

        return ListBucketsResponse.builder()
                .owner(OwnerDto.builder()
                        .id(account.getAccessKeyId())
                        .displayName(account.getAccountName())
                        .build())
                .buckets(bucketDtos)
                .build();
    }

    /**
     * Get bucket versioning status.
     */
    public Bucket.VersioningStatus getVersioningStatus(String bucketName) {
        Bucket bucket = getBucket(bucketName);
        return bucket.getVersioningStatus();
    }

    /**
     * Set bucket versioning status.
     */
    @Transactional
    @CacheEvict(value = "buckets", key = "#bucketName")
    public void setVersioningStatus(String bucketName, Bucket.VersioningStatus status, Long accountId) {
        Bucket bucket = getBucket(bucketName);

        // Verify ownership
        if (!bucket.getOwnerAccountId().equals(accountId)) {
            throw S3Exception.accessDenied();
        }

        // Cannot disable versioning once enabled (only suspend)
        if (bucket.getVersioningStatus() == Bucket.VersioningStatus.ENABLED &&
                status == Bucket.VersioningStatus.DISABLED) {
            throw S3Exception.illegalVersioningConfiguration();
        }

        bucket.setVersioningStatus(status);
        bucketRepository.save(bucket);

        log.info("[BUCKET] Updated versioning for {}: {}", bucketName, status);
    }

    /**
     * Validate bucket name according to S3 rules.
     */
    private void validateBucketName(String name) {
        if (name == null || name.isEmpty()) {
            throw S3Exception.invalidBucketName("Bucket name cannot be empty");
        }

        if (name.length() < 3 || name.length() > 63) {
            throw S3Exception.invalidBucketName("Bucket name must be between 3 and 63 characters");
        }

        if (!BUCKET_NAME_PATTERN.matcher(name).matches()) {
            throw S3Exception.invalidBucketName("Bucket name does not follow naming rules");
        }

        // Cannot look like IP address
        if (name.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            throw S3Exception.invalidBucketName("Bucket name cannot be an IP address");
        }

        // Cannot start with xn-- (reserved)
        if (name.startsWith("xn--")) {
            throw S3Exception.invalidBucketName("Bucket name cannot start with xn--");
        }

        // Cannot end with -s3alias or --ol-s3
        if (name.endsWith("-s3alias") || name.endsWith("--ol-s3")) {
            throw S3Exception.invalidBucketName("Bucket name has reserved suffix");
        }
    }

    /**
     * Get bucket statistics.
     */
    public BucketStats getBucketStats(String bucketName) {
        Bucket bucket = getBucket(bucketName);
        return new BucketStats(
                bucket.getObjectCount(),
                bucket.getTotalSizeBytes());
    }

    public record BucketStats(long objectCount, long totalSizeBytes) {
    }
}
