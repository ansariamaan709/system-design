package com.amazons3.service;

import com.amazons3.dto.CompleteMultipartUploadRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultipartUploadService Tests")
class MultipartUploadServiceTest {

    @Mock
    private MultipartUploadRepository uploadRepository;

    @Mock
    private MultipartPartRepository partRepository;

    @Mock
    private BucketRepository bucketRepository;

    @Mock
    private ObjectRepository objectRepository;

    @Mock
    private StorageEngine storageEngine;

    @InjectMocks
    private MultipartUploadService multipartUploadService;

    private Bucket testBucket;
    private MultipartUpload testUpload;

    @BeforeEach
    void setUp() {
        testBucket = new Bucket();
        testBucket.setId(UUID.randomUUID());
        testBucket.setName("test-bucket");
        testBucket.setVersioningStatus(Bucket.VersioningStatus.DISABLED);

        testUpload = new MultipartUpload();
        testUpload.setId(UUID.randomUUID());
        testUpload.setUploadId(UUID.randomUUID().toString());
        testUpload.setBucket(testBucket);
        testUpload.setObjectKey("large-file.zip");
        testUpload.setContentType("application/zip");
        testUpload.setStatus(MultipartUpload.UploadStatus.IN_PROGRESS);
        testUpload.setInitiatedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("initiateMultipartUpload")
    class InitiateMultipartUploadTests {

        @Test
        @DisplayName("should initiate multipart upload")
        void shouldInitiateMultipartUpload() {
            // Given
            String objectKey = "large-file.zip";
            String contentType = "application/zip";

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.save(any(MultipartUpload.class))).thenAnswer(invocation -> {
                MultipartUpload upload = invocation.getArgument(0);
                upload.setId(UUID.randomUUID());
                upload.setUploadId(UUID.randomUUID().toString());
                upload.setInitiatedAt(LocalDateTime.now());
                return upload;
            });

            // When
            MultipartUpload result = multipartUploadService.initiateMultipartUpload(
                    testBucket.getName(), objectKey, contentType, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUploadId()).isNotNull();
            assertThat(result.getObjectKey()).isEqualTo(objectKey);
            assertThat(result.getContentType()).isEqualTo(contentType);
            assertThat(result.getStatus()).isEqualTo(MultipartUpload.UploadStatus.IN_PROGRESS);

            verify(uploadRepository).save(any(MultipartUpload.class));
        }

        @Test
        @DisplayName("should throw exception when bucket not found")
        void shouldThrowExceptionWhenBucketNotFound() {
            // Given
            when(bucketRepository.findByName("non-existent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> multipartUploadService.initiateMultipartUpload(
                    "non-existent", "file.txt", "text/plain", null))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("NoSuchBucket");
        }
    }

    @Nested
    @DisplayName("uploadPart")
    class UploadPartTests {

        @Test
        @DisplayName("should upload part successfully")
        void shouldUploadPartSuccessfully() throws IOException {
            // Given
            int partNumber = 1;
            byte[] partData = new byte[5 * 1024 * 1024]; // 5MB
            Arrays.fill(partData, (byte) 'A');
            String partHash = "sha256parthash";

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(testUpload.getUploadId(), testBucket.getId()))
                    .thenReturn(Optional.of(testUpload));
            when(partRepository.findByUploadIdAndPartNumber(testUpload.getId(), partNumber))
                    .thenReturn(Optional.empty());
            when(storageEngine.store(any(InputStream.class))).thenReturn(partHash);
            when(storageEngine.getStoragePath(partHash)).thenReturn("ab/cd/" + partHash);
            when(partRepository.save(any(MultipartPart.class))).thenAnswer(invocation -> {
                MultipartPart part = invocation.getArgument(0);
                part.setId(UUID.randomUUID());
                part.setUploadedAt(LocalDateTime.now());
                return part;
            });

            // When
            MultipartPart result = multipartUploadService.uploadPart(
                    testBucket.getName(),
                    testUpload.getObjectKey(),
                    testUpload.getUploadId(),
                    partNumber,
                    new ByteArrayInputStream(partData),
                    (long) partData.length);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getPartNumber()).isEqualTo(partNumber);
            assertThat(result.getSize()).isEqualTo(partData.length);
            assertThat(result.getEtag()).isNotNull();

            verify(storageEngine).store(any(InputStream.class));
            verify(partRepository).save(any(MultipartPart.class));
        }

        @Test
        @DisplayName("should overwrite existing part")
        void shouldOverwriteExistingPart() throws IOException {
            // Given
            int partNumber = 1;
            byte[] newPartData = "new part data".getBytes();

            MultipartPart existingPart = new MultipartPart();
            existingPart.setId(UUID.randomUUID());
            existingPart.setUpload(testUpload);
            existingPart.setPartNumber(partNumber);
            existingPart.setStoragePath("old/path");

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(testUpload.getUploadId(), testBucket.getId()))
                    .thenReturn(Optional.of(testUpload));
            when(partRepository.findByUploadIdAndPartNumber(testUpload.getId(), partNumber))
                    .thenReturn(Optional.of(existingPart));
            when(storageEngine.store(any(InputStream.class))).thenReturn("newhash");
            when(storageEngine.getStoragePath(anyString())).thenReturn("ab/cd/newhash");
            when(partRepository.save(any(MultipartPart.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            MultipartPart result = multipartUploadService.uploadPart(
                    testBucket.getName(),
                    testUpload.getObjectKey(),
                    testUpload.getUploadId(),
                    partNumber,
                    new ByteArrayInputStream(newPartData),
                    (long) newPartData.length);

            // Then
            verify(partRepository).delete(existingPart);
            verify(storageEngine).delete(existingPart.getStoragePath());
            assertThat(result.getStoragePath()).isEqualTo("ab/cd/newhash");
        }

        @Test
        @DisplayName("should throw exception when upload not found")
        void shouldThrowExceptionWhenUploadNotFound() {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(anyString(), any(UUID.class)))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> multipartUploadService.uploadPart(
                    testBucket.getName(),
                    "file.txt",
                    "invalid-upload-id",
                    1,
                    new ByteArrayInputStream("data".getBytes()),
                    4L))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("NoSuchUpload");
        }

        @Test
        @DisplayName("should throw exception when part number is invalid")
        void shouldThrowExceptionWhenPartNumberIsInvalid() {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(testUpload.getUploadId(), testBucket.getId()))
                    .thenReturn(Optional.of(testUpload));

            // When & Then - Part number 0
            assertThatThrownBy(() -> multipartUploadService.uploadPart(
                    testBucket.getName(),
                    testUpload.getObjectKey(),
                    testUpload.getUploadId(),
                    0,
                    new ByteArrayInputStream("data".getBytes()),
                    4L))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("InvalidPart");

            // Part number > 10000
            assertThatThrownBy(() -> multipartUploadService.uploadPart(
                    testBucket.getName(),
                    testUpload.getObjectKey(),
                    testUpload.getUploadId(),
                    10001,
                    new ByteArrayInputStream("data".getBytes()),
                    4L))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("InvalidPart");
        }
    }

    @Nested
    @DisplayName("completeMultipartUpload")
    class CompleteMultipartUploadTests {

        @Test
        @DisplayName("should complete multipart upload")
        void shouldCompleteMultipartUpload() throws IOException {
            // Given
            MultipartPart part1 = createPart(1, 5 * 1024 * 1024L, "etag1");
            MultipartPart part2 = createPart(2, 5 * 1024 * 1024L, "etag2");
            MultipartPart part3 = createPart(3, 1024L, "etag3"); // Last part can be smaller

            List<CompleteMultipartUploadRequest.Part> requestParts = Arrays.asList(
                    new CompleteMultipartUploadRequest.Part(1, "\"etag1\""),
                    new CompleteMultipartUploadRequest.Part(2, "\"etag2\""),
                    new CompleteMultipartUploadRequest.Part(3, "\"etag3\""));
            CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(requestParts);

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(testUpload.getUploadId(), testBucket.getId()))
                    .thenReturn(Optional.of(testUpload));
            when(partRepository.findByUploadIdOrderByPartNumber(testUpload.getId()))
                    .thenReturn(Arrays.asList(part1, part2, part3));
            when(storageEngine.concatenate(anyList())).thenReturn("finalhash");
            when(storageEngine.getStoragePath("finalhash")).thenReturn("ab/cd/finalhash");
            when(objectRepository.findLatestVersion(testBucket.getId(), testUpload.getObjectKey()))
                    .thenReturn(Optional.empty());
            when(objectRepository.save(any(S3Object.class))).thenAnswer(invocation -> {
                S3Object obj = invocation.getArgument(0);
                obj.setId(UUID.randomUUID());
                obj.setCreatedAt(LocalDateTime.now());
                return obj;
            });

            // When
            S3Object result = multipartUploadService.completeMultipartUpload(
                    testBucket.getName(),
                    testUpload.getObjectKey(),
                    testUpload.getUploadId(),
                    request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getObjectKey()).isEqualTo(testUpload.getObjectKey());
            assertThat(result.getStoragePath()).isEqualTo("ab/cd/finalhash");
            assertThat(result.getSize()).isEqualTo(10 * 1024 * 1024L + 1024L);

            verify(storageEngine).concatenate(anyList());
            verify(uploadRepository).save(argThat(u -> u.getStatus() == MultipartUpload.UploadStatus.COMPLETED));
        }

        @Test
        @DisplayName("should throw exception when no parts uploaded")
        void shouldThrowExceptionWhenNoPartsUploaded() {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(testUpload.getUploadId(), testBucket.getId()))
                    .thenReturn(Optional.of(testUpload));
            when(partRepository.findByUploadIdOrderByPartNumber(testUpload.getId()))
                    .thenReturn(Collections.emptyList());

            CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(Collections.emptyList());

            // When & Then
            assertThatThrownBy(() -> multipartUploadService.completeMultipartUpload(
                    testBucket.getName(),
                    testUpload.getObjectKey(),
                    testUpload.getUploadId(),
                    request))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("InvalidPart");
        }

        @Test
        @DisplayName("should throw exception when parts are not in sequence")
        void shouldThrowExceptionWhenPartsNotInSequence() {
            // Given
            MultipartPart part1 = createPart(1, 5 * 1024 * 1024L, "etag1");
            MultipartPart part3 = createPart(3, 1024L, "etag3"); // Missing part 2

            List<CompleteMultipartUploadRequest.Part> requestParts = Arrays.asList(
                    new CompleteMultipartUploadRequest.Part(1, "\"etag1\""),
                    new CompleteMultipartUploadRequest.Part(3, "\"etag3\""));
            CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(requestParts);

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(testUpload.getUploadId(), testBucket.getId()))
                    .thenReturn(Optional.of(testUpload));
            when(partRepository.findByUploadIdOrderByPartNumber(testUpload.getId()))
                    .thenReturn(Arrays.asList(part1, part3));

            // When & Then
            assertThatThrownBy(() -> multipartUploadService.completeMultipartUpload(
                    testBucket.getName(),
                    testUpload.getObjectKey(),
                    testUpload.getUploadId(),
                    request))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("InvalidPart");
        }

        @Test
        @DisplayName("should throw exception when etag mismatch")
        void shouldThrowExceptionWhenEtagMismatch() {
            // Given
            MultipartPart part1 = createPart(1, 5 * 1024 * 1024L, "etag1");

            List<CompleteMultipartUploadRequest.Part> requestParts = Arrays.asList(
                    new CompleteMultipartUploadRequest.Part(1, "\"wrongetag\""));
            CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(requestParts);

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(testUpload.getUploadId(), testBucket.getId()))
                    .thenReturn(Optional.of(testUpload));
            when(partRepository.findByUploadIdOrderByPartNumber(testUpload.getId()))
                    .thenReturn(Arrays.asList(part1));

            // When & Then
            assertThatThrownBy(() -> multipartUploadService.completeMultipartUpload(
                    testBucket.getName(),
                    testUpload.getObjectKey(),
                    testUpload.getUploadId(),
                    request))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("InvalidPart");
        }

        private MultipartPart createPart(int partNumber, long size, String etag) {
            MultipartPart part = new MultipartPart();
            part.setId(UUID.randomUUID());
            part.setUpload(testUpload);
            part.setPartNumber(partNumber);
            part.setSize(size);
            part.setEtag(etag);
            part.setStoragePath("path/part" + partNumber);
            part.setUploadedAt(LocalDateTime.now());
            return part;
        }
    }

    @Nested
    @DisplayName("abortMultipartUpload")
    class AbortMultipartUploadTests {

        @Test
        @DisplayName("should abort multipart upload and clean up parts")
        void shouldAbortMultipartUploadAndCleanUpParts() throws IOException {
            // Given
            MultipartPart part1 = new MultipartPart();
            part1.setId(UUID.randomUUID());
            part1.setUpload(testUpload);
            part1.setPartNumber(1);
            part1.setStoragePath("path/part1");

            MultipartPart part2 = new MultipartPart();
            part2.setId(UUID.randomUUID());
            part2.setUpload(testUpload);
            part2.setPartNumber(2);
            part2.setStoragePath("path/part2");

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(testUpload.getUploadId(), testBucket.getId()))
                    .thenReturn(Optional.of(testUpload));
            when(partRepository.findByUploadIdOrderByPartNumber(testUpload.getId()))
                    .thenReturn(Arrays.asList(part1, part2));

            // When
            multipartUploadService.abortMultipartUpload(
                    testBucket.getName(),
                    testUpload.getObjectKey(),
                    testUpload.getUploadId());

            // Then
            verify(storageEngine).delete("path/part1");
            verify(storageEngine).delete("path/part2");
            verify(partRepository).deleteAll(anyList());
            verify(uploadRepository).save(argThat(u -> u.getStatus() == MultipartUpload.UploadStatus.ABORTED));
        }

        @Test
        @DisplayName("should throw exception when upload not found")
        void shouldThrowExceptionWhenUploadNotFound() {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(anyString(), any(UUID.class)))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> multipartUploadService.abortMultipartUpload(
                    testBucket.getName(),
                    "file.txt",
                    "invalid-upload-id"))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("NoSuchUpload");
        }
    }

    @Nested
    @DisplayName("listParts")
    class ListPartsTests {

        @Test
        @DisplayName("should list all parts for upload")
        void shouldListAllPartsForUpload() {
            // Given
            MultipartPart part1 = new MultipartPart();
            part1.setPartNumber(1);
            part1.setSize(5 * 1024 * 1024L);
            part1.setEtag("etag1");
            part1.setUploadedAt(LocalDateTime.now());

            MultipartPart part2 = new MultipartPart();
            part2.setPartNumber(2);
            part2.setSize(3 * 1024 * 1024L);
            part2.setEtag("etag2");
            part2.setUploadedAt(LocalDateTime.now());

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByUploadIdAndBucketId(testUpload.getUploadId(), testBucket.getId()))
                    .thenReturn(Optional.of(testUpload));
            when(partRepository.findByUploadIdOrderByPartNumber(testUpload.getId()))
                    .thenReturn(Arrays.asList(part1, part2));

            // When
            List<MultipartPart> result = multipartUploadService.listParts(
                    testBucket.getName(),
                    testUpload.getObjectKey(),
                    testUpload.getUploadId());

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getPartNumber()).isEqualTo(1);
            assertThat(result.get(1).getPartNumber()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("listMultipartUploads")
    class ListMultipartUploadsTests {

        @Test
        @DisplayName("should list all in-progress uploads for bucket")
        void shouldListAllInProgressUploadsForBucket() {
            // Given
            MultipartUpload upload1 = new MultipartUpload();
            upload1.setUploadId(UUID.randomUUID().toString());
            upload1.setObjectKey("file1.zip");
            upload1.setStatus(MultipartUpload.UploadStatus.IN_PROGRESS);
            upload1.setInitiatedAt(LocalDateTime.now());

            MultipartUpload upload2 = new MultipartUpload();
            upload2.setUploadId(UUID.randomUUID().toString());
            upload2.setObjectKey("file2.zip");
            upload2.setStatus(MultipartUpload.UploadStatus.IN_PROGRESS);
            upload2.setInitiatedAt(LocalDateTime.now());

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(uploadRepository.findByBucketIdAndStatus(testBucket.getId(), MultipartUpload.UploadStatus.IN_PROGRESS))
                    .thenReturn(Arrays.asList(upload1, upload2));

            // When
            List<MultipartUpload> result = multipartUploadService.listMultipartUploads(testBucket.getName());

            // Then
            assertThat(result).hasSize(2);
        }
    }
}
