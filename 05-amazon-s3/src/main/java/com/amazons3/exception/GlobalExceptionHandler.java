package com.amazons3.exception;

import com.amazons3.dto.S3ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

/**
 * Global exception handler for S3 API errors.
 * Returns XML error responses in S3 format.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<S3ErrorResponse> handleS3Exception(S3Exception ex) {
        String requestId = generateRequestId();

        S3ErrorResponse error = S3ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .resource(ex.getResource())
                .requestId(requestId)
                .build();

        log.warn("[S3ERROR] {} - {} (requestId: {})", ex.getErrorCode(), ex.getMessage(), requestId);

        return ResponseEntity
                .status(ex.getHttpStatus())
                .contentType(MediaType.APPLICATION_XML)
                .body(error);
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<S3ErrorResponse> handleFileNotFound(FileNotFoundException ex) {
        String requestId = generateRequestId();

        S3ErrorResponse error = S3ErrorResponse.builder()
                .code("NoSuchKey")
                .message("The specified key does not exist")
                .requestId(requestId)
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .body(error);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<S3ErrorResponse> handleIOException(IOException ex) {
        String requestId = generateRequestId();

        log.error("[S3ERROR] IO error: {} (requestId: {})", ex.getMessage(), requestId, ex);

        S3ErrorResponse error = S3ErrorResponse.builder()
                .code("InternalError")
                .message("We encountered an internal error. Please try again.")
                .requestId(requestId)
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_XML)
                .body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<S3ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String requestId = generateRequestId();

        S3ErrorResponse error = S3ErrorResponse.builder()
                .code("InvalidArgument")
                .message(ex.getMessage())
                .requestId(requestId)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_XML)
                .body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<S3ErrorResponse> handleGenericException(Exception ex) {
        String requestId = generateRequestId();

        log.error("[S3ERROR] Unexpected error: {} (requestId: {})", ex.getMessage(), requestId, ex);

        S3ErrorResponse error = S3ErrorResponse.builder()
                .code("InternalError")
                .message("We encountered an internal error. Please try again.")
                .requestId(requestId)
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_XML)
                .body(error);
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().toUpperCase().replace("-", "");
    }
}
