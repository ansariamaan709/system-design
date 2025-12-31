package com.amazons3.controller;

import com.amazons3.dto.*;
import com.amazons3.entity.MultipartPart;
import com.amazons3.service.MultipartUploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Controller for multipart upload operations.
 * Implements S3 multipart upload API endpoints.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MultipartController {

    private final MultipartUploadService multipartUploadService;

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    /**
     * Initiate multipart upload (POST /{bucket}/{key}?uploads)
     */
    @PostMapping(value = "/{bucket}/**", params = "uploads", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<InitiateMultipartUploadResponse> initiateMultipartUpload(
            @PathVariable("bucket") String bucketName,
            HttpServletRequest request,
            @RequestHeader(value = "Content-Type", defaultValue = "application/octet-stream") String contentType,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId) {

        String key = extractKey(request, bucketName);
        Long effectiveAccountId = accountId != null ? accountId : DEFAULT_ACCOUNT_ID;

        InitiateMultipartUploadResponse response = multipartUploadService.initiateUpload(
                bucketName, key, contentType, effectiveAccountId);

        return ResponseEntity.ok(response);
    }

    /**
     * Upload part (PUT /{bucket}/{key}?partNumber&uploadId)
     */
    @PutMapping(value = "/{bucket}/**", params = { "partNumber", "uploadId" })
    public ResponseEntity<Void> uploadPart(
            @PathVariable("bucket") String bucketName,
            HttpServletRequest request,
            @RequestParam("partNumber") int partNumber,
            @RequestParam("uploadId") String uploadId,
            InputStream requestBody) throws IOException {

        String key = extractKey(request, bucketName);

        String etag = multipartUploadService.uploadPart(bucketName, key, uploadId, partNumber, requestBody);

        return ResponseEntity.ok()
                .eTag(etag)
                .build();
    }

    /**
     * Complete multipart upload (POST /{bucket}/{key}?uploadId)
     */
    @PostMapping(value = "/{bucket}/**", params = "uploadId", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<CompleteMultipartUploadResponse> completeMultipartUpload(
            @PathVariable("bucket") String bucketName,
            HttpServletRequest request,
            @RequestParam("uploadId") String uploadId,
            @RequestBody CompleteMultipartUploadRequest completeRequest) throws IOException {

        String key = extractKey(request, bucketName);

        CompleteMultipartUploadResponse response = multipartUploadService.completeUpload(
                bucketName, key, uploadId, completeRequest);

        return ResponseEntity.ok(response);
    }

    /**
     * Abort multipart upload (DELETE /{bucket}/{key}?uploadId)
     */
    @DeleteMapping(value = "/{bucket}/**", params = "uploadId")
    public ResponseEntity<Void> abortMultipartUpload(
            @PathVariable("bucket") String bucketName,
            HttpServletRequest request,
            @RequestParam("uploadId") String uploadId) {

        String key = extractKey(request, bucketName);

        multipartUploadService.abortUpload(bucketName, key, uploadId);

        return ResponseEntity.noContent().build();
    }

    /**
     * List parts (GET /{bucket}/{key}?uploadId)
     */
    @GetMapping(value = "/{bucket}/**", params = "uploadId", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> listParts(
            @PathVariable("bucket") String bucketName,
            HttpServletRequest request,
            @RequestParam("uploadId") String uploadId,
            @RequestParam(value = "part-number-marker", required = false) Integer partNumberMarker,
            @RequestParam(value = "max-parts", defaultValue = "1000") int maxParts) {

        String key = extractKey(request, bucketName);

        List<MultipartPart> parts = multipartUploadService.listParts(uploadId);

        // Build XML response
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<ListPartsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        xml.append("<Bucket>").append(bucketName).append("</Bucket>");
        xml.append("<Key>").append(key).append("</Key>");
        xml.append("<UploadId>").append(uploadId).append("</UploadId>");
        xml.append("<MaxParts>").append(maxParts).append("</MaxParts>");
        xml.append("<IsTruncated>false</IsTruncated>");

        for (MultipartPart part : parts) {
            xml.append("<Part>");
            xml.append("<PartNumber>").append(part.getPartNumber()).append("</PartNumber>");
            xml.append("<LastModified>").append(part.getUploadedAt()).append("</LastModified>");
            xml.append("<ETag>").append(part.getEtag()).append("</ETag>");
            xml.append("<Size>").append(part.getSizeBytes()).append("</Size>");
            xml.append("</Part>");
        }

        xml.append("</ListPartsResult>");

        return ResponseEntity.ok(xml.toString());
    }

    /**
     * List multipart uploads for bucket (GET /{bucket}?uploads)
     */
    @GetMapping(value = "/{bucket}", params = "uploads", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> listMultipartUploads(
            @PathVariable("bucket") String bucketName,
            @RequestParam(value = "prefix", required = false) String prefix,
            @RequestParam(value = "max-uploads", defaultValue = "1000") int maxUploads) {

        var uploads = multipartUploadService.listUploads(bucketName, prefix);

        // Build XML response
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<ListMultipartUploadsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        xml.append("<Bucket>").append(bucketName).append("</Bucket>");
        xml.append("<MaxUploads>").append(maxUploads).append("</MaxUploads>");
        xml.append("<IsTruncated>false</IsTruncated>");

        for (var upload : uploads) {
            xml.append("<Upload>");
            xml.append("<Key>").append(upload.getObjectKey()).append("</Key>");
            xml.append("<UploadId>").append(upload.getUploadId()).append("</UploadId>");
            xml.append("<StorageClass>").append(upload.getStorageClass()).append("</StorageClass>");
            xml.append("<Initiated>").append(upload.getInitiatedAt()).append("</Initiated>");
            xml.append("</Upload>");
        }

        xml.append("</ListMultipartUploadsResult>");

        return ResponseEntity.ok(xml.toString());
    }

    private String extractKey(HttpServletRequest request, String bucketName) {
        String path = request.getRequestURI();
        return path.substring(("/" + bucketName + "/").length());
    }
}
