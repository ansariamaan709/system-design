package com.amazons3.controller;

import com.amazons3.dto.ListBucketsResponse;
import com.amazons3.entity.Bucket;
import com.amazons3.service.BucketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for bucket operations.
 * Implements S3 bucket API endpoints.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BucketController {

    private final BucketService bucketService;

    // Default account ID for development (would come from authentication in
    // production)
    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    /**
     * List all buckets (GET /)
     */
    @GetMapping(value = "/", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<ListBucketsResponse> listBuckets(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId) {

        Long effectiveAccountId = accountId != null ? accountId : DEFAULT_ACCOUNT_ID;
        ListBucketsResponse response = bucketService.listBuckets(effectiveAccountId);
        return ResponseEntity.ok(response);
    }

    /**
     * Create bucket (PUT /{bucket})
     */
    @PutMapping(value = "/{bucket}", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<Void> createBucket(
            @PathVariable("bucket") String bucketName,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            @RequestHeader(value = "X-Amz-Bucket-Object-Lock-Enabled", required = false) Boolean objectLockEnabled,
            @RequestBody(required = false) String createBucketConfiguration) {

        Long effectiveAccountId = accountId != null ? accountId : DEFAULT_ACCOUNT_ID;

        // Parse region from configuration if provided
        String region = null;
        if (createBucketConfiguration != null && !createBucketConfiguration.isEmpty()) {
            // Simple region extraction - in production would use proper XML parsing
            if (createBucketConfiguration.contains("<LocationConstraint>")) {
                int start = createBucketConfiguration.indexOf("<LocationConstraint>") + 20;
                int end = createBucketConfiguration.indexOf("</LocationConstraint>");
                if (end > start) {
                    region = createBucketConfiguration.substring(start, end);
                }
            }
        }

        Bucket bucket = bucketService.createBucket(bucketName, effectiveAccountId, region);

        return ResponseEntity
                .status(HttpStatus.OK)
                .header("Location", "/" + bucketName)
                .build();
    }

    /**
     * Delete bucket (DELETE /{bucket})
     */
    @DeleteMapping(value = "/{bucket}")
    public ResponseEntity<Void> deleteBucket(
            @PathVariable("bucket") String bucketName,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId) {

        Long effectiveAccountId = accountId != null ? accountId : DEFAULT_ACCOUNT_ID;
        bucketService.deleteBucket(bucketName, effectiveAccountId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Head bucket (HEAD /{bucket}) - Check if bucket exists
     */
    @RequestMapping(value = "/{bucket}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headBucket(@PathVariable("bucket") String bucketName) {
        bucketService.getBucket(bucketName);
        return ResponseEntity.ok().build();
    }

    /**
     * Get bucket versioning (GET /{bucket}?versioning)
     */
    @GetMapping(value = "/{bucket}", params = "versioning", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getBucketVersioning(@PathVariable("bucket") String bucketName) {
        Bucket.VersioningStatus status = bucketService.getVersioningStatus(bucketName);

        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">";

        if (status != Bucket.VersioningStatus.DISABLED) {
            response += "<Status>" + (status == Bucket.VersioningStatus.ENABLED ? "Enabled" : "Suspended")
                    + "</Status>";
        }

        response += "</VersioningConfiguration>";

        return ResponseEntity.ok(response);
    }

    /**
     * Set bucket versioning (PUT /{bucket}?versioning)
     */
    @PutMapping(value = "/{bucket}", params = "versioning")
    public ResponseEntity<Void> setBucketVersioning(
            @PathVariable("bucket") String bucketName,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            @RequestBody String versioningConfiguration) {

        Long effectiveAccountId = accountId != null ? accountId : DEFAULT_ACCOUNT_ID;

        // Parse status from configuration
        Bucket.VersioningStatus status;
        if (versioningConfiguration.contains("<Status>Enabled</Status>")) {
            status = Bucket.VersioningStatus.ENABLED;
        } else if (versioningConfiguration.contains("<Status>Suspended</Status>")) {
            status = Bucket.VersioningStatus.SUSPENDED;
        } else {
            status = Bucket.VersioningStatus.DISABLED;
        }

        bucketService.setVersioningStatus(bucketName, status, effectiveAccountId);
        return ResponseEntity.ok().build();
    }

    /**
     * Get bucket location (GET /{bucket}?location)
     */
    @GetMapping(value = "/{bucket}", params = "location", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getBucketLocation(@PathVariable("bucket") String bucketName) {
        Bucket bucket = bucketService.getBucket(bucketName);

        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<LocationConstraint xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" +
                bucket.getRegion() +
                "</LocationConstraint>";

        return ResponseEntity.ok(response);
    }
}
