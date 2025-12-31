package com.amazons3.controller;

import com.amazons3.dto.*;
import com.amazons3.entity.S3Object;
import com.amazons3.service.ObjectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for object operations.
 * Implements S3 object API endpoints.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ObjectController {

    private final ObjectService objectService;

    private static final DateTimeFormatter HTTP_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME
            .withZone(ZoneOffset.UTC);

    /**
     * List objects (GET /{bucket}?list-type=2)
     */
    @GetMapping(value = "/{bucket}", params = "list-type", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<ListObjectsV2Response> listObjectsV2(
            @PathVariable("bucket") String bucketName,
            @RequestParam(value = "list-type") int listType,
            @RequestParam(value = "prefix", required = false) String prefix,
            @RequestParam(value = "delimiter", required = false) String delimiter,
            @RequestParam(value = "start-after", required = false) String startAfter,
            @RequestParam(value = "continuation-token", required = false) String continuationToken,
            @RequestParam(value = "max-keys", defaultValue = "1000") int maxKeys) {

        ListObjectsV2Response response = objectService.listObjects(
                bucketName, prefix, delimiter, startAfter, continuationToken, maxKeys);

        return ResponseEntity.ok(response);
    }

    /**
     * Put object (PUT /{bucket}/{key})
     */
    @PutMapping(value = "/{bucket}/**")
    public ResponseEntity<Void> putObject(
            @PathVariable("bucket") String bucketName,
            HttpServletRequest request,
            @RequestHeader(value = "Content-Type", defaultValue = "application/octet-stream") String contentType,
            @RequestHeader(value = "x-amz-copy-source", required = false) String copySource,
            InputStream requestBody) throws IOException {

        String key = extractKey(request, bucketName);

        // Handle copy operation
        if (copySource != null) {
            return handleCopyObject(copySource, bucketName, key);
        }

        // Extract user metadata from headers
        Map<String, String> userMetadata = extractUserMetadata(request);

        S3Object object = objectService.putObject(bucketName, key, requestBody, contentType, userMetadata);

        return ResponseEntity.ok()
                .eTag(object.getEtag())
                .header("x-amz-version-id", object.getVersionId())
                .build();
    }

    /**
     * Get object (GET /{bucket}/{key})
     */
    @GetMapping(value = "/{bucket}/**")
    public ResponseEntity<InputStreamResource> getObject(
            @PathVariable("bucket") String bucketName,
            HttpServletRequest request,
            @RequestParam(value = "versionId", required = false) String versionId,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
            @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) throws IOException {

        String key = extractKey(request, bucketName);

        // Check for list-type parameter (should be handled by listObjectsV2)
        if (request.getParameter("list-type") != null) {
            // This shouldn't happen due to mapping priority, but just in case
            throw new IllegalArgumentException("Invalid request");
        }

        S3Object object = objectService.getObject(bucketName, key, versionId);

        // Handle conditional requests
        if (ifMatch != null && !object.getEtag().equals(ifMatch)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        }
        if (ifNoneMatch != null && object.getEtag().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        // Handle range request
        if (rangeHeader != null) {
            return handleRangeRequest(object, rangeHeader);
        }

        // Full object retrieval
        InputStream data = objectService.getObjectData(object);

        HttpHeaders headers = buildObjectHeaders(object);

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(object.getSizeBytes())
                .contentType(MediaType.parseMediaType(object.getContentType()))
                .body(new InputStreamResource(data));
    }

    /**
     * Head object (HEAD /{bucket}/{key})
     */
    @RequestMapping(value = "/{bucket}/**", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headObject(
            @PathVariable("bucket") String bucketName,
            HttpServletRequest request,
            @RequestParam(value = "versionId", required = false) String versionId) {

        String key = extractKey(request, bucketName);

        S3Object object = objectService.headObject(bucketName, key, versionId);

        HttpHeaders headers = buildObjectHeaders(object);

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(object.getSizeBytes())
                .build();
    }

    /**
     * Delete object (DELETE /{bucket}/{key})
     */
    @DeleteMapping(value = "/{bucket}/**")
    public ResponseEntity<Void> deleteObject(
            @PathVariable("bucket") String bucketName,
            HttpServletRequest request,
            @RequestParam(value = "versionId", required = false) String versionId) {

        String key = extractKey(request, bucketName);

        ObjectService.DeleteResult result = objectService.deleteObject(bucketName, key, versionId);

        ResponseEntity.BodyBuilder response = ResponseEntity.noContent();

        if (result.versionId() != null) {
            response.header("x-amz-version-id", result.versionId());
        }
        if (result.deleteMarker()) {
            response.header("x-amz-delete-marker", "true");
        }

        return response.build();
    }

    // ==================== Helper Methods ====================

    private String extractKey(HttpServletRequest request, String bucketName) {
        String path = request.getRequestURI();
        // Remove bucket prefix
        String keyPath = path.substring(("/" + bucketName + "/").length());
        // URL decode if needed
        return keyPath;
    }

    private Map<String, String> extractUserMetadata(HttpServletRequest request) {
        Map<String, String> metadata = new HashMap<>();
        var headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name.toLowerCase().startsWith("x-amz-meta-")) {
                String metaKey = name.substring("x-amz-meta-".length());
                metadata.put(metaKey, request.getHeader(name));
            }
        }
        return metadata;
    }

    private HttpHeaders buildObjectHeaders(S3Object object) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(object.getEtag());
        headers.setLastModified(object.getLastModified());
        headers.add("x-amz-version-id", object.getVersionId());
        headers.add("x-amz-storage-class", object.getStorageClass().name());

        if (object.getContentEncoding() != null) {
            headers.add(HttpHeaders.CONTENT_ENCODING, object.getContentEncoding());
        }
        if (object.getContentDisposition() != null) {
            headers.add(HttpHeaders.CONTENT_DISPOSITION, object.getContentDisposition());
        }
        if (object.getCacheControl() != null) {
            headers.add(HttpHeaders.CACHE_CONTROL, object.getCacheControl());
        }
        if (object.getExpiresAt() != null) {
            headers.add("x-amz-expiration", HTTP_DATE_FORMAT.format(object.getExpiresAt()));
        }

        return headers;
    }

    private ResponseEntity<InputStreamResource> handleRangeRequest(S3Object object, String rangeHeader)
            throws IOException {

        // Parse range header: "bytes=start-end"
        if (!rangeHeader.startsWith("bytes=")) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }

        String range = rangeHeader.substring(6);
        String[] parts = range.split("-");

        long start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
        long end = parts.length > 1 && !parts[1].isEmpty()
                ? Long.parseLong(parts[1])
                : object.getSizeBytes() - 1;

        // Validate range
        if (start >= object.getSizeBytes() || end >= object.getSizeBytes() || start > end) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + object.getSizeBytes())
                    .build();
        }

        long contentLength = end - start + 1;
        InputStream data = objectService.getObjectDataRange(object, start, end);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header("Content-Range", "bytes " + start + "-" + end + "/" + object.getSizeBytes())
                .contentLength(contentLength)
                .contentType(MediaType.parseMediaType(object.getContentType()))
                .eTag(object.getEtag())
                .body(new InputStreamResource(data));
    }

    private ResponseEntity<Void> handleCopyObject(String copySource, String destBucket, String destKey) {
        // Parse copy source: /bucket/key or bucket/key
        String source = copySource.startsWith("/") ? copySource.substring(1) : copySource;
        int slashIndex = source.indexOf('/');
        if (slashIndex == -1) {
            throw new IllegalArgumentException("Invalid x-amz-copy-source");
        }

        String sourceBucket = source.substring(0, slashIndex);
        String sourceKey = source.substring(slashIndex + 1);

        // Handle version ID in source
        String sourceVersionId = null;
        int versionIndex = sourceKey.indexOf("?versionId=");
        if (versionIndex != -1) {
            sourceVersionId = sourceKey.substring(versionIndex + 11);
            sourceKey = sourceKey.substring(0, versionIndex);
        }

        S3Object copy = objectService.copyObject(sourceBucket, sourceKey, sourceVersionId,
                destBucket, destKey, null);

        return ResponseEntity.ok()
                .eTag(copy.getEtag())
                .header("x-amz-version-id", copy.getVersionId())
                .header("x-amz-copy-source-version-id", sourceVersionId)
                .build();
    }
}
