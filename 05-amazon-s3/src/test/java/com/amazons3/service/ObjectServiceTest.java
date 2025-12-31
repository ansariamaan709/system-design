package com.amazons3.service;

import com.amazons3.dto.ListObjectsV2Response;
import com.amazons3.dto.ObjectDto;
import com.amazons3.entity.Account;
import com.amazons3.entity.Bucket;
import com.amazons3.entity.S3Object;
import com.amazons3.exception.S3Exception;
import com.amazons3.repository.BucketRepository;
import com.amazons3.repository.ObjectMetadataRepository;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ObjectService Tests")
class ObjectServiceTest {

    @Mock
    private ObjectRepository objectRepository;

    @Mock
    private BucketRepository bucketRepository;

    @Mock
    private ObjectMetadataRepository metadataRepository;

    @Mock
    private StorageEngine storageEngine;

    @InjectMocks
    private ObjectService objectService;

    private Account testAccount;
    private Bucket testBucket;
    private S3Object testObject;

    @BeforeEach
    void setUp() {
        testAccount = new Account();
        testAccount.setId(UUID.randomUUID());
        testAccount.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        testAccount.setAccountName("TestAccount");

        testBucket = new Bucket();
        testBucket.setId(UUID.randomUUID());
        testBucket.setName("test-bucket");
        testBucket.setAccount(testAccount);
        testBucket.setVersioningStatus(Bucket.VersioningStatus.DISABLED);
        testBucket.setCreatedAt(LocalDateTime.now());

        testObject = new S3Object();
        testObject.setId(UUID.randomUUID());
        testObject.setBucket(testBucket);
        testObject.setObjectKey("test-file.txt");
        testObject.setVersionId("null");
        testObject.setSize(1024L);
        testObject.setContentType("text/plain");
        testObject.setStoragePath("ab/cd/abcdef123456...");
        testObject.setContentHash("abcdef123456789...");
        testObject.setIsLatest(true);
        testObject.setIsDeleteMarker(false);
        testObject.setCreatedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("putObject")
    class PutObjectTests {

        @Test
        @DisplayName("should create new object in non-versioned bucket")
        void shouldCreateNewObjectInNonVersionedBucket() throws IOException {
            // Given
            String objectKey = "new-file.txt";
            byte[] content = "Hello, S3!".getBytes();
            String contentHash = "sha256hash123";
            String storagePath = "ab/cd/sha256hash123";

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), objectKey)).thenReturn(Optional.empty());
            when(storageEngine.store(any(InputStream.class))).thenReturn(contentHash);
            when(storageEngine.getStoragePath(contentHash)).thenReturn(storagePath);
            when(objectRepository.save(any(S3Object.class))).thenAnswer(invocation -> {
                S3Object obj = invocation.getArgument(0);
                obj.setId(UUID.randomUUID());
                obj.setCreatedAt(LocalDateTime.now());
                return obj;
            });

            // When
            S3Object result = objectService.putObject(
                    testBucket.getName(),
                    objectKey,
                    new ByteArrayInputStream(content),
                    (long) content.length,
                    "text/plain",
                    null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getObjectKey()).isEqualTo(objectKey);
            assertThat(result.getVersionId()).isEqualTo("null");
            assertThat(result.getIsLatest()).isTrue();

            verify(storageEngine).store(any(InputStream.class));
            verify(objectRepository).save(any(S3Object.class));
        }

        @Test
        @DisplayName("should create versioned object in versioned bucket")
        void shouldCreateVersionedObjectInVersionedBucket() throws IOException {
            // Given
            testBucket.setVersioningStatus(Bucket.VersioningStatus.ENABLED);
            String objectKey = "versioned-file.txt";
            byte[] content = "Version 1".getBytes();

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), objectKey)).thenReturn(Optional.empty());
            when(storageEngine.store(any(InputStream.class))).thenReturn("sha256hash");
            when(storageEngine.getStoragePath(anyString())).thenReturn("ab/cd/sha256hash");
            when(objectRepository.save(any(S3Object.class))).thenAnswer(invocation -> {
                S3Object obj = invocation.getArgument(0);
                obj.setId(UUID.randomUUID());
                obj.setCreatedAt(LocalDateTime.now());
                return obj;
            });

            // When
            S3Object result = objectService.putObject(
                    testBucket.getName(),
                    objectKey,
                    new ByteArrayInputStream(content),
                    (long) content.length,
                    "text/plain",
                    null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getVersionId()).isNotEqualTo("null");
            assertThat(result.getIsLatest()).isTrue();
        }

        @Test
        @DisplayName("should mark previous version as not latest when uploading new version")
        void shouldMarkPreviousVersionAsNotLatest() throws IOException {
            // Given
            testBucket.setVersioningStatus(Bucket.VersioningStatus.ENABLED);
            S3Object existingObject = new S3Object();
            existingObject.setId(UUID.randomUUID());
            existingObject.setObjectKey("file.txt");
            existingObject.setIsLatest(true);
            existingObject.setVersionId(UUID.randomUUID().toString());

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), "file.txt"))
                    .thenReturn(Optional.of(existingObject));
            when(storageEngine.store(any(InputStream.class))).thenReturn("newhash");
            when(storageEngine.getStoragePath(anyString())).thenReturn("ab/cd/newhash");
            when(objectRepository.save(any(S3Object.class))).thenAnswer(invocation -> {
                S3Object obj = invocation.getArgument(0);
                obj.setId(UUID.randomUUID());
                obj.setCreatedAt(LocalDateTime.now());
                return obj;
            });

            // When
            objectService.putObject(
                    testBucket.getName(),
                    "file.txt",
                    new ByteArrayInputStream("New content".getBytes()),
                    11L,
                    "text/plain",
                    null);

            // Then
            assertThat(existingObject.getIsLatest()).isFalse();
            verify(objectRepository, times(2)).save(any(S3Object.class));
        }

        @Test
        @DisplayName("should throw exception when bucket not found")
        void shouldThrowExceptionWhenBucketNotFound() {
            // Given
            when(bucketRepository.findByName("non-existent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> objectService.putObject(
                    "non-existent",
                    "file.txt",
                    new ByteArrayInputStream("data".getBytes()),
                    4L,
                    "text/plain",
                    null))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("NoSuchBucket");
        }
    }

    @Nested
    @DisplayName("getObject")
    class GetObjectTests {

        @Test
        @DisplayName("should return object content")
        void shouldReturnObjectContent() throws IOException {
            // Given
            byte[] content = "Hello, S3!".getBytes();
            Resource resource = new ByteArrayResource(content);

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), testObject.getObjectKey()))
                    .thenReturn(Optional.of(testObject));
            when(storageEngine.retrieve(testObject.getStoragePath())).thenReturn(resource);

            // When
            Resource result = objectService.getObject(testBucket.getName(), testObject.getObjectKey(), null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.contentLength()).isEqualTo(content.length);
        }

        @Test
        @DisplayName("should return specific version when versionId provided")
        void shouldReturnSpecificVersionWhenVersionIdProvided() throws IOException {
            // Given
            String versionId = "specific-version-id";
            S3Object versionedObject = new S3Object();
            versionedObject.setId(UUID.randomUUID());
            versionedObject.setBucket(testBucket);
            versionedObject.setObjectKey("file.txt");
            versionedObject.setVersionId(versionId);
            versionedObject.setStoragePath("ab/cd/versionhash");
            versionedObject.setIsDeleteMarker(false);

            byte[] content = "Version content".getBytes();
            Resource resource = new ByteArrayResource(content);

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findByBucketIdAndObjectKeyAndVersionId(testBucket.getId(), "file.txt", versionId))
                    .thenReturn(Optional.of(versionedObject));
            when(storageEngine.retrieve(versionedObject.getStoragePath())).thenReturn(resource);

            // When
            Resource result = objectService.getObject(testBucket.getName(), "file.txt", versionId);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw exception when object not found")
        void shouldThrowExceptionWhenObjectNotFound() {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), "missing.txt"))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> objectService.getObject(testBucket.getName(), "missing.txt", null))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("NoSuchKey");
        }

        @Test
        @DisplayName("should throw exception when accessing delete marker")
        void shouldThrowExceptionWhenAccessingDeleteMarker() {
            // Given
            S3Object deleteMarker = new S3Object();
            deleteMarker.setId(UUID.randomUUID());
            deleteMarker.setObjectKey("deleted.txt");
            deleteMarker.setIsDeleteMarker(true);

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), "deleted.txt"))
                    .thenReturn(Optional.of(deleteMarker));

            // When & Then
            assertThatThrownBy(() -> objectService.getObject(testBucket.getName(), "deleted.txt", null))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("NoSuchKey");
        }
    }

    @Nested
    @DisplayName("deleteObject")
    class DeleteObjectTests {

        @Test
        @DisplayName("should delete object in non-versioned bucket")
        void shouldDeleteObjectInNonVersionedBucket() throws IOException {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), testObject.getObjectKey()))
                    .thenReturn(Optional.of(testObject));
            when(objectRepository.countByStoragePath(testObject.getStoragePath())).thenReturn(1L);

            // When
            String result = objectService.deleteObject(testBucket.getName(), testObject.getObjectKey(), null);

            // Then
            assertThat(result).isNull(); // Non-versioned returns null
            verify(objectRepository).delete(testObject);
            verify(storageEngine).delete(testObject.getStoragePath());
        }

        @Test
        @DisplayName("should create delete marker in versioned bucket")
        void shouldCreateDeleteMarkerInVersionedBucket() throws IOException {
            // Given
            testBucket.setVersioningStatus(Bucket.VersioningStatus.ENABLED);

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), testObject.getObjectKey()))
                    .thenReturn(Optional.of(testObject));
            when(objectRepository.save(any(S3Object.class))).thenAnswer(invocation -> {
                S3Object obj = invocation.getArgument(0);
                obj.setId(UUID.randomUUID());
                return obj;
            });

            // When
            String result = objectService.deleteObject(testBucket.getName(), testObject.getObjectKey(), null);

            // Then
            assertThat(result).isNotNull(); // Returns delete marker version ID

            ArgumentCaptor<S3Object> captor = ArgumentCaptor.forClass(S3Object.class);
            verify(objectRepository, atLeastOnce()).save(captor.capture());

            // Find the delete marker in saved objects
            boolean hasDeleteMarker = captor.getAllValues().stream()
                    .anyMatch(S3Object::getIsDeleteMarker);
            assertThat(hasDeleteMarker).isTrue();
        }

        @Test
        @DisplayName("should delete specific version when versionId provided")
        void shouldDeleteSpecificVersionWhenVersionIdProvided() throws IOException {
            // Given
            testBucket.setVersioningStatus(Bucket.VersioningStatus.ENABLED);
            String versionId = "specific-version";
            S3Object versionedObject = new S3Object();
            versionedObject.setId(UUID.randomUUID());
            versionedObject.setBucket(testBucket);
            versionedObject.setObjectKey("file.txt");
            versionedObject.setVersionId(versionId);
            versionedObject.setStoragePath("ab/cd/hash");
            versionedObject.setIsDeleteMarker(false);
            versionedObject.setIsLatest(false);

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findByBucketIdAndObjectKeyAndVersionId(testBucket.getId(), "file.txt", versionId))
                    .thenReturn(Optional.of(versionedObject));
            when(objectRepository.countByStoragePath(versionedObject.getStoragePath())).thenReturn(1L);

            // When
            String result = objectService.deleteObject(testBucket.getName(), "file.txt", versionId);

            // Then
            assertThat(result).isEqualTo(versionId);
            verify(objectRepository).delete(versionedObject);
        }

        @Test
        @DisplayName("should not delete storage when content is shared")
        void shouldNotDeleteStorageWhenContentIsShared() throws IOException {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), testObject.getObjectKey()))
                    .thenReturn(Optional.of(testObject));
            when(objectRepository.countByStoragePath(testObject.getStoragePath())).thenReturn(2L); // Shared

            // When
            objectService.deleteObject(testBucket.getName(), testObject.getObjectKey(), null);

            // Then
            verify(objectRepository).delete(testObject);
            verify(storageEngine, never()).delete(anyString()); // Should not delete shared content
        }
    }

    @Nested
    @DisplayName("listObjects")
    class ListObjectsTests {

        @Test
        @DisplayName("should list objects with prefix filter")
        void shouldListObjectsWithPrefixFilter() {
            // Given
            String prefix = "documents/";
            S3Object obj1 = new S3Object();
            obj1.setObjectKey("documents/file1.txt");
            obj1.setSize(1024L);
            obj1.setCreatedAt(LocalDateTime.now());
            obj1.setIsLatest(true);
            obj1.setVersionId("null");

            S3Object obj2 = new S3Object();
            obj2.setObjectKey("documents/file2.txt");
            obj2.setSize(2048L);
            obj2.setCreatedAt(LocalDateTime.now());
            obj2.setIsLatest(true);
            obj2.setVersionId("null");

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findByBucketIdAndObjectKeyStartingWith(testBucket.getId(), prefix))
                    .thenReturn(Arrays.asList(obj1, obj2));

            // When
            ListObjectsV2Response result = objectService.listObjectsV2(
                    testBucket.getName(), prefix, null, null, 1000, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContents()).hasSize(2);
            assertThat(result.getKeyCount()).isEqualTo(2);
            assertThat(result.getPrefix()).isEqualTo(prefix);
        }

        @Test
        @DisplayName("should return common prefixes with delimiter")
        void shouldReturnCommonPrefixesWithDelimiter() {
            // Given
            String prefix = "";
            String delimiter = "/";

            S3Object obj1 = new S3Object();
            obj1.setObjectKey("folder1/file1.txt");
            obj1.setSize(1024L);
            obj1.setCreatedAt(LocalDateTime.now());
            obj1.setIsLatest(true);

            S3Object obj2 = new S3Object();
            obj2.setObjectKey("folder1/file2.txt");
            obj2.setSize(2048L);
            obj2.setCreatedAt(LocalDateTime.now());
            obj2.setIsLatest(true);

            S3Object obj3 = new S3Object();
            obj3.setObjectKey("folder2/file3.txt");
            obj3.setSize(512L);
            obj3.setCreatedAt(LocalDateTime.now());
            obj3.setIsLatest(true);

            S3Object obj4 = new S3Object();
            obj4.setObjectKey("root-file.txt");
            obj4.setSize(256L);
            obj4.setCreatedAt(LocalDateTime.now());
            obj4.setIsLatest(true);

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findByBucketIdAndObjectKeyStartingWith(testBucket.getId(), prefix))
                    .thenReturn(Arrays.asList(obj1, obj2, obj3, obj4));

            // When
            ListObjectsV2Response result = objectService.listObjectsV2(
                    testBucket.getName(), prefix, delimiter, null, 1000, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCommonPrefixes()).containsExactlyInAnyOrder("folder1/", "folder2/");
            assertThat(result.getContents()).hasSize(1); // Only root-file.txt
        }

        @Test
        @DisplayName("should limit results to maxKeys")
        void shouldLimitResultsToMaxKeys() {
            // Given
            List<S3Object> objects = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                S3Object obj = new S3Object();
                obj.setObjectKey("file" + i + ".txt");
                obj.setSize(1024L);
                obj.setCreatedAt(LocalDateTime.now());
                obj.setIsLatest(true);
                objects.add(obj);
            }

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findByBucketIdAndObjectKeyStartingWith(testBucket.getId(), ""))
                    .thenReturn(objects);

            // When
            ListObjectsV2Response result = objectService.listObjectsV2(
                    testBucket.getName(), "", null, null, 5, null);

            // Then
            assertThat(result.getContents()).hasSize(5);
            assertThat(result.getIsTruncated()).isTrue();
            assertThat(result.getNextContinuationToken()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getObjectMetadata")
    class GetObjectMetadataTests {

        @Test
        @DisplayName("should return object metadata")
        void shouldReturnObjectMetadata() {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), testObject.getObjectKey()))
                    .thenReturn(Optional.of(testObject));

            // When
            S3Object result = objectService.getObjectMetadata(testBucket.getName(), testObject.getObjectKey(), null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSize()).isEqualTo(testObject.getSize());
            assertThat(result.getContentType()).isEqualTo(testObject.getContentType());
        }
    }

    @Nested
    @DisplayName("copyObject")
    class CopyObjectTests {

        @Test
        @DisplayName("should copy object to same bucket")
        void shouldCopyObjectToSameBucket() throws IOException {
            // Given
            String destKey = "copied-file.txt";

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), testObject.getObjectKey()))
                    .thenReturn(Optional.of(testObject));
            when(objectRepository.findLatestVersion(testBucket.getId(), destKey))
                    .thenReturn(Optional.empty());
            when(objectRepository.save(any(S3Object.class))).thenAnswer(invocation -> {
                S3Object obj = invocation.getArgument(0);
                obj.setId(UUID.randomUUID());
                obj.setCreatedAt(LocalDateTime.now());
                return obj;
            });

            // When
            S3Object result = objectService.copyObject(
                    testBucket.getName(),
                    testObject.getObjectKey(),
                    testBucket.getName(),
                    destKey);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getObjectKey()).isEqualTo(destKey);
            assertThat(result.getStoragePath()).isEqualTo(testObject.getStoragePath()); // Same storage path (dedup)
        }

        @Test
        @DisplayName("should copy object to different bucket")
        void shouldCopyObjectToDifferentBucket() throws IOException {
            // Given
            Bucket destBucket = new Bucket();
            destBucket.setId(UUID.randomUUID());
            destBucket.setName("dest-bucket");
            destBucket.setVersioningStatus(Bucket.VersioningStatus.DISABLED);

            String destKey = "copied-file.txt";

            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(bucketRepository.findByName(destBucket.getName())).thenReturn(Optional.of(destBucket));
            when(objectRepository.findLatestVersion(testBucket.getId(), testObject.getObjectKey()))
                    .thenReturn(Optional.of(testObject));
            when(objectRepository.findLatestVersion(destBucket.getId(), destKey))
                    .thenReturn(Optional.empty());
            when(objectRepository.save(any(S3Object.class))).thenAnswer(invocation -> {
                S3Object obj = invocation.getArgument(0);
                obj.setId(UUID.randomUUID());
                obj.setCreatedAt(LocalDateTime.now());
                return obj;
            });

            // When
            S3Object result = objectService.copyObject(
                    testBucket.getName(),
                    testObject.getObjectKey(),
                    destBucket.getName(),
                    destKey);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getBucket().getName()).isEqualTo(destBucket.getName());
        }
    }
}
