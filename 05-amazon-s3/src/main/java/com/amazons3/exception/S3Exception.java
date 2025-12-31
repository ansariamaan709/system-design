package com.amazons3.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * S3-specific exception with error code and HTTP status.
 */
@Getter
public class S3Exception extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final String resource;

    public S3Exception(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.resource = null;
    }

    public S3Exception(String errorCode, String message, HttpStatus httpStatus, String resource) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.resource = resource;
    }

    // ==================== Bucket Errors ====================

    public static S3Exception noSuchBucket(String bucketName) {
        return new S3Exception("NoSuchBucket",
                "The specified bucket does not exist",
                HttpStatus.NOT_FOUND, bucketName);
    }

    public static S3Exception bucketAlreadyExists(String bucketName) {
        return new S3Exception("BucketAlreadyExists",
                "The requested bucket name is not available",
                HttpStatus.CONFLICT, bucketName);
    }

    public static S3Exception bucketNotEmpty(String bucketName) {
        return new S3Exception("BucketNotEmpty",
                "The bucket you tried to delete is not empty",
                HttpStatus.CONFLICT, bucketName);
    }

    public static S3Exception tooManyBuckets() {
        return new S3Exception("TooManyBuckets",
                "You have attempted to create more buckets than allowed",
                HttpStatus.BAD_REQUEST);
    }

    public static S3Exception invalidBucketName(String message) {
        return new S3Exception("InvalidBucketName", message, HttpStatus.BAD_REQUEST);
    }

    public static S3Exception illegalVersioningConfiguration() {
        return new S3Exception("IllegalVersioningConfigurationException",
                "Versioning cannot be disabled once enabled",
                HttpStatus.BAD_REQUEST);
    }

    // ==================== Object Errors ====================

    public static S3Exception noSuchKey(String key) {
        return new S3Exception("NoSuchKey",
                "The specified key does not exist",
                HttpStatus.NOT_FOUND, key);
    }

    public static S3Exception invalidObjectState(String message) {
        return new S3Exception("InvalidObjectState", message, HttpStatus.FORBIDDEN);
    }

    // ==================== Multipart Errors ====================

    public static S3Exception noSuchUpload(String uploadId) {
        return new S3Exception("NoSuchUpload",
                "The specified multipart upload does not exist",
                HttpStatus.NOT_FOUND, uploadId);
    }

    public static S3Exception invalidPartNumber(int partNumber) {
        return new S3Exception("InvalidPartNumber",
                "Part number must be between 1 and 10000: " + partNumber,
                HttpStatus.BAD_REQUEST);
    }

    public static S3Exception invalidPart(int partNumber) {
        return new S3Exception("InvalidPart",
                "One or more of the specified parts could not be found: " + partNumber,
                HttpStatus.BAD_REQUEST);
    }

    public static S3Exception entityTooSmall(int partNumber) {
        return new S3Exception("EntityTooSmall",
                "Your proposed upload is smaller than the minimum allowed size: part " + partNumber,
                HttpStatus.BAD_REQUEST);
    }

    public static S3Exception entityTooLarge() {
        return new S3Exception("EntityTooLarge",
                "Your proposed upload exceeds the maximum allowed size",
                HttpStatus.BAD_REQUEST);
    }

    // ==================== Access Errors ====================

    public static S3Exception accessDenied() {
        return new S3Exception("AccessDenied", "Access Denied", HttpStatus.FORBIDDEN);
    }

    public static S3Exception accessDenied(String message) {
        return new S3Exception("AccessDenied", message, HttpStatus.FORBIDDEN);
    }

    public static S3Exception invalidAccessKeyId() {
        return new S3Exception("InvalidAccessKeyId",
                "The AWS Access Key Id you provided does not exist in our records",
                HttpStatus.FORBIDDEN);
    }

    public static S3Exception signatureDoesNotMatch() {
        return new S3Exception("SignatureDoesNotMatch",
                "The request signature we calculated does not match the signature you provided",
                HttpStatus.FORBIDDEN);
    }

    // ==================== Request Errors ====================

    public static S3Exception invalidRequest(String message) {
        return new S3Exception("InvalidRequest", message, HttpStatus.BAD_REQUEST);
    }

    public static S3Exception invalidArgument(String message) {
        return new S3Exception("InvalidArgument", message, HttpStatus.BAD_REQUEST);
    }

    public static S3Exception missingContentLength() {
        return new S3Exception("MissingContentLength",
                "You must provide the Content-Length HTTP header",
                HttpStatus.LENGTH_REQUIRED);
    }

    public static S3Exception invalidRange() {
        return new S3Exception("InvalidRange",
                "The requested range is not satisfiable",
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    public static S3Exception preconditionFailed() {
        return new S3Exception("PreconditionFailed",
                "At least one of the pre-conditions you specified did not hold",
                HttpStatus.PRECONDITION_FAILED);
    }

    // ==================== Internal Errors ====================

    public static S3Exception internalError(String message) {
        return new S3Exception("InternalError", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static S3Exception serviceUnavailable() {
        return new S3Exception("ServiceUnavailable",
                "Service is unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static S3Exception slowDown() {
        return new S3Exception("SlowDown",
                "Please reduce your request rate",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
