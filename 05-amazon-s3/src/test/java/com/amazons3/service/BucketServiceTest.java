package com.amazons3.service;

import com.amazons3.dto.ListBucketsResponse;
import com.amazons3.entity.Account;
import com.amazons3.entity.Bucket;
import com.amazons3.exception.S3Exception;
import com.amazons3.repository.AccountRepository;
import com.amazons3.repository.BucketRepository;
import com.amazons3.repository.ObjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BucketService Tests")
class BucketServiceTest {

    @Mock
    private BucketRepository bucketRepository;

    @Mock
    private ObjectRepository objectRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private BucketService bucketService;

    private Account testAccount;
    private Bucket testBucket;

    @BeforeEach
    void setUp() {
        testAccount = new Account();
        testAccount.setId(UUID.randomUUID());
        testAccount.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        testAccount.setSecretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        testAccount.setAccountName("TestAccount");
        testAccount.setEmail("test@example.com");
        testAccount.setCreatedAt(LocalDateTime.now());

        testBucket = new Bucket();
        testBucket.setId(UUID.randomUUID());
        testBucket.setName("test-bucket");
        testBucket.setAccount(testAccount);
        testBucket.setRegion("us-east-1");
        testBucket.setVersioningStatus(Bucket.VersioningStatus.DISABLED);
        testBucket.setCreatedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("createBucket")
    class CreateBucketTests {

        @Test
        @DisplayName("should create bucket with valid name")
        void shouldCreateBucketWithValidName() {
            // Given
            String bucketName = "my-valid-bucket";
            when(bucketRepository.existsByName(bucketName)).thenReturn(false);
            when(accountRepository.findByAccessKeyId(anyString())).thenReturn(Optional.of(testAccount));
            when(bucketRepository.save(any(Bucket.class))).thenAnswer(invocation -> {
                Bucket bucket = invocation.getArgument(0);
                bucket.setId(UUID.randomUUID());
                bucket.setCreatedAt(LocalDateTime.now());
                return bucket;
            });

            // When
            Bucket result = bucketService.createBucket(bucketName, "us-east-1", testAccount.getAccessKeyId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(bucketName);
            assertThat(result.getRegion()).isEqualTo("us-east-1");
            assertThat(result.getVersioningStatus()).isEqualTo(Bucket.VersioningStatus.DISABLED);

            ArgumentCaptor<Bucket> bucketCaptor = ArgumentCaptor.forClass(Bucket.class);
            verify(bucketRepository).save(bucketCaptor.capture());
            assertThat(bucketCaptor.getValue().getName()).isEqualTo(bucketName);
        }

        @Test
        @DisplayName("should throw exception when bucket name already exists")
        void shouldThrowExceptionWhenBucketNameExists() {
            // Given
            String bucketName = "existing-bucket";
            when(bucketRepository.existsByName(bucketName)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> bucketService.createBucket(bucketName, "us-east-1", testAccount.getAccessKeyId()))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("BucketAlreadyExists");
        }

        @Test
        @DisplayName("should throw exception when bucket name is too short")
        void shouldThrowExceptionWhenBucketNameTooShort() {
            // Given
            String bucketName = "ab"; // Less than 3 characters

            // When & Then
            assertThatThrownBy(() -> bucketService.createBucket(bucketName, "us-east-1", testAccount.getAccessKeyId()))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("InvalidBucketName");
        }

        @Test
        @DisplayName("should throw exception when bucket name is too long")
        void shouldThrowExceptionWhenBucketNameTooLong() {
            // Given
            String bucketName = "a".repeat(64); // More than 63 characters

            // When & Then
            assertThatThrownBy(() -> bucketService.createBucket(bucketName, "us-east-1", testAccount.getAccessKeyId()))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("InvalidBucketName");
        }

        @Test
        @DisplayName("should throw exception when bucket name contains uppercase letters")
        void shouldThrowExceptionWhenBucketNameContainsUppercase() {
            // Given
            String bucketName = "MyBucket";

            // When & Then
            assertThatThrownBy(() -> bucketService.createBucket(bucketName, "us-east-1", testAccount.getAccessKeyId()))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("InvalidBucketName");
        }

        @Test
        @DisplayName("should throw exception when bucket name starts with hyphen")
        void shouldThrowExceptionWhenBucketNameStartsWithHyphen() {
            // Given
            String bucketName = "-invalid-bucket";

            // When & Then
            assertThatThrownBy(() -> bucketService.createBucket(bucketName, "us-east-1", testAccount.getAccessKeyId()))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("InvalidBucketName");
        }

        @Test
        @DisplayName("should throw exception when bucket name looks like IP address")
        void shouldThrowExceptionWhenBucketNameLooksLikeIpAddress() {
            // Given
            String bucketName = "192.168.1.1";

            // When & Then
            assertThatThrownBy(() -> bucketService.createBucket(bucketName, "us-east-1", testAccount.getAccessKeyId()))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("InvalidBucketName");
        }

        @Test
        @DisplayName("should allow bucket name with numbers and hyphens")
        void shouldAllowBucketNameWithNumbersAndHyphens() {
            // Given
            String bucketName = "my-bucket-2024";
            when(bucketRepository.existsByName(bucketName)).thenReturn(false);
            when(accountRepository.findByAccessKeyId(anyString())).thenReturn(Optional.of(testAccount));
            when(bucketRepository.save(any(Bucket.class))).thenAnswer(invocation -> {
                Bucket bucket = invocation.getArgument(0);
                bucket.setId(UUID.randomUUID());
                return bucket;
            });

            // When
            Bucket result = bucketService.createBucket(bucketName, "us-east-1", testAccount.getAccessKeyId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(bucketName);
        }
    }

    @Nested
    @DisplayName("deleteBucket")
    class DeleteBucketTests {

        @Test
        @DisplayName("should delete empty bucket")
        void shouldDeleteEmptyBucket() {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.countActiveObjectsInBucket(testBucket.getId())).thenReturn(0L);

            // When
            bucketService.deleteBucket(testBucket.getName());

            // Then
            verify(bucketRepository).delete(testBucket);
        }

        @Test
        @DisplayName("should throw exception when bucket not found")
        void shouldThrowExceptionWhenBucketNotFound() {
            // Given
            String bucketName = "non-existent-bucket";
            when(bucketRepository.findByName(bucketName)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> bucketService.deleteBucket(bucketName))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("NoSuchBucket");
        }

        @Test
        @DisplayName("should throw exception when bucket is not empty")
        void shouldThrowExceptionWhenBucketNotEmpty() {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(objectRepository.countActiveObjectsInBucket(testBucket.getId())).thenReturn(5L);

            // When & Then
            assertThatThrownBy(() -> bucketService.deleteBucket(testBucket.getName()))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("BucketNotEmpty");
        }
    }

    @Nested
    @DisplayName("getBucket")
    class GetBucketTests {

        @Test
        @DisplayName("should return bucket when exists")
        void shouldReturnBucketWhenExists() {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));

            // When
            Bucket result = bucketService.getBucket(testBucket.getName());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(testBucket.getName());
        }

        @Test
        @DisplayName("should throw exception when bucket not found")
        void shouldThrowExceptionWhenBucketNotFound() {
            // Given
            String bucketName = "non-existent-bucket";
            when(bucketRepository.findByName(bucketName)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> bucketService.getBucket(bucketName))
                    .isInstanceOf(S3Exception.class)
                    .hasMessageContaining("NoSuchBucket");
        }
    }

    @Nested
    @DisplayName("listBuckets")
    class ListBucketsTests {

        @Test
        @DisplayName("should return list of buckets")
        void shouldReturnListOfBuckets() {
            // Given
            Bucket bucket1 = new Bucket();
            bucket1.setName("bucket-1");
            bucket1.setCreatedAt(LocalDateTime.now());

            Bucket bucket2 = new Bucket();
            bucket2.setName("bucket-2");
            bucket2.setCreatedAt(LocalDateTime.now());

            when(accountRepository.findByAccessKeyId(testAccount.getAccessKeyId()))
                    .thenReturn(Optional.of(testAccount));
            when(bucketRepository.findByAccount(testAccount))
                    .thenReturn(Arrays.asList(bucket1, bucket2));

            // When
            ListBucketsResponse result = bucketService.listBuckets(testAccount.getAccessKeyId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getBuckets()).hasSize(2);
            assertThat(result.getOwner()).isNotNull();
            assertThat(result.getOwner().getDisplayName()).isEqualTo(testAccount.getAccountName());
        }

        @Test
        @DisplayName("should return empty list when no buckets")
        void shouldReturnEmptyListWhenNoBuckets() {
            // Given
            when(accountRepository.findByAccessKeyId(testAccount.getAccessKeyId()))
                    .thenReturn(Optional.of(testAccount));
            when(bucketRepository.findByAccount(testAccount))
                    .thenReturn(Collections.emptyList());

            // When
            ListBucketsResponse result = bucketService.listBuckets(testAccount.getAccessKeyId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getBuckets()).isEmpty();
        }
    }

    @Nested
    @DisplayName("setVersioning")
    class SetVersioningTests {

        @Test
        @DisplayName("should enable versioning")
        void shouldEnableVersioning() {
            // Given
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(bucketRepository.save(any(Bucket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            bucketService.setVersioning(testBucket.getName(), Bucket.VersioningStatus.ENABLED);

            // Then
            ArgumentCaptor<Bucket> bucketCaptor = ArgumentCaptor.forClass(Bucket.class);
            verify(bucketRepository).save(bucketCaptor.capture());
            assertThat(bucketCaptor.getValue().getVersioningStatus()).isEqualTo(Bucket.VersioningStatus.ENABLED);
        }

        @Test
        @DisplayName("should suspend versioning")
        void shouldSuspendVersioning() {
            // Given
            testBucket.setVersioningStatus(Bucket.VersioningStatus.ENABLED);
            when(bucketRepository.findByName(testBucket.getName())).thenReturn(Optional.of(testBucket));
            when(bucketRepository.save(any(Bucket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            bucketService.setVersioning(testBucket.getName(), Bucket.VersioningStatus.SUSPENDED);

            // Then
            ArgumentCaptor<Bucket> bucketCaptor = ArgumentCaptor.forClass(Bucket.class);
            verify(bucketRepository).save(bucketCaptor.capture());
            assertThat(bucketCaptor.getValue().getVersioningStatus()).isEqualTo(Bucket.VersioningStatus.SUSPENDED);
        }
    }

    @Nested
    @DisplayName("bucketExists")
    class BucketExistsTests {

        @Test
        @DisplayName("should return true when bucket exists")
        void shouldReturnTrueWhenBucketExists() {
            // Given
            when(bucketRepository.existsByName(testBucket.getName())).thenReturn(true);

            // When
            boolean result = bucketService.bucketExists(testBucket.getName());

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when bucket does not exist")
        void shouldReturnFalseWhenBucketDoesNotExist() {
            // Given
            when(bucketRepository.existsByName("non-existent")).thenReturn(false);

            // When
            boolean result = bucketService.bucketExists("non-existent");

            // Then
            assertThat(result).isFalse();
        }
    }
}
