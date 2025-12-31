package com.amazons3.storage;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("StorageEngine Tests")
class StorageEngineTest {

    @TempDir
    Path tempDir;

    private StorageEngine storageEngine;
    private Path storagePath;
    private Path tempPath;

    @BeforeEach
    void setUp() {
        storagePath = tempDir.resolve("storage");
        tempPath = tempDir.resolve("temp");
        storageEngine = new StorageEngine(storagePath.toString(), tempPath.toString());
    }

    @Nested
    @DisplayName("store")
    class StoreTests {

        @Test
        @DisplayName("should store content and return SHA-256 hash")
        void shouldStoreContentAndReturnHash() throws IOException, NoSuchAlgorithmException {
            // Given
            byte[] content = "Hello, S3!".getBytes();
            String expectedHash = calculateSha256(content);

            // When
            String hash = storageEngine.store(new ByteArrayInputStream(content));

            // Then
            assertThat(hash).isEqualTo(expectedHash);

            // Verify file exists at expected location
            String expectedPath = storageEngine.getStoragePath(hash);
            Path fullPath = storagePath.resolve(expectedPath);
            assertThat(Files.exists(fullPath)).isTrue();
            assertThat(Files.readAllBytes(fullPath)).isEqualTo(content);
        }

        @Test
        @DisplayName("should deduplicate identical content")
        void shouldDeduplicateIdenticalContent() throws IOException {
            // Given
            byte[] content = "Duplicate content".getBytes();

            // When
            String hash1 = storageEngine.store(new ByteArrayInputStream(content));
            String hash2 = storageEngine.store(new ByteArrayInputStream(content));

            // Then
            assertThat(hash1).isEqualTo(hash2);

            // Verify only one file exists
            String path = storageEngine.getStoragePath(hash1);
            assertThat(Files.exists(storagePath.resolve(path))).isTrue();
        }

        @Test
        @DisplayName("should store different content with different hashes")
        void shouldStoreDifferentContentWithDifferentHashes() throws IOException {
            // Given
            byte[] content1 = "Content 1".getBytes();
            byte[] content2 = "Content 2".getBytes();

            // When
            String hash1 = storageEngine.store(new ByteArrayInputStream(content1));
            String hash2 = storageEngine.store(new ByteArrayInputStream(content2));

            // Then
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("should handle large content")
        void shouldHandleLargeContent() throws IOException {
            // Given
            byte[] content = new byte[10 * 1024 * 1024]; // 10MB
            Arrays.fill(content, (byte) 'A');

            // When
            String hash = storageEngine.store(new ByteArrayInputStream(content));

            // Then
            assertThat(hash).isNotNull();
            assertThat(hash).hasSize(64); // SHA-256 produces 64 hex characters

            // Verify content integrity
            String path = storageEngine.getStoragePath(hash);
            byte[] stored = Files.readAllBytes(storagePath.resolve(path));
            assertThat(stored).isEqualTo(content);
        }

        @Test
        @DisplayName("should handle empty content")
        void shouldHandleEmptyContent() throws IOException {
            // Given
            byte[] content = new byte[0];

            // When
            String hash = storageEngine.store(new ByteArrayInputStream(content));

            // Then
            assertThat(hash).isNotNull();
            // SHA-256 of empty string
            assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        }
    }

    @Nested
    @DisplayName("retrieve")
    class RetrieveTests {

        @Test
        @DisplayName("should retrieve stored content")
        void shouldRetrieveStoredContent() throws IOException {
            // Given
            byte[] content = "Test content for retrieval".getBytes();
            String hash = storageEngine.store(new ByteArrayInputStream(content));
            String path = storageEngine.getStoragePath(hash);

            // When
            Resource resource = storageEngine.retrieve(path);

            // Then
            assertThat(resource).isNotNull();
            assertThat(resource.exists()).isTrue();
            assertThat(resource.contentLength()).isEqualTo(content.length);

            try (InputStream is = resource.getInputStream()) {
                byte[] retrieved = is.readAllBytes();
                assertThat(retrieved).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should throw exception for non-existent file")
        void shouldThrowExceptionForNonExistentFile() {
            // Given
            String nonExistentPath = "ab/cd/nonexistent";

            // When & Then
            assertThatThrownBy(() -> storageEngine.retrieve(nonExistentPath))
                    .isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("retrieveRange")
    class RetrieveRangeTests {

        @Test
        @DisplayName("should retrieve partial content with range")
        void shouldRetrievePartialContentWithRange() throws IOException {
            // Given
            byte[] content = "0123456789ABCDEFGHIJ".getBytes();
            String hash = storageEngine.store(new ByteArrayInputStream(content));
            String path = storageEngine.getStoragePath(hash);

            // When - Get bytes 5-9 (inclusive)
            Resource resource = storageEngine.retrieveRange(path, 5, 9);

            // Then
            assertThat(resource).isNotNull();
            try (InputStream is = resource.getInputStream()) {
                byte[] retrieved = is.readAllBytes();
                assertThat(new String(retrieved)).isEqualTo("56789");
            }
        }

        @Test
        @DisplayName("should retrieve from start to end when end exceeds content length")
        void shouldRetrieveToEndWhenEndExceedsLength() throws IOException {
            // Given
            byte[] content = "Short".getBytes();
            String hash = storageEngine.store(new ByteArrayInputStream(content));
            String path = storageEngine.getStoragePath(hash);

            // When - Request range beyond content length
            Resource resource = storageEngine.retrieveRange(path, 2, 100);

            // Then
            try (InputStream is = resource.getInputStream()) {
                byte[] retrieved = is.readAllBytes();
                assertThat(new String(retrieved)).isEqualTo("ort");
            }
        }

        @Test
        @DisplayName("should retrieve single byte")
        void shouldRetrieveSingleByte() throws IOException {
            // Given
            byte[] content = "ABCDE".getBytes();
            String hash = storageEngine.store(new ByteArrayInputStream(content));
            String path = storageEngine.getStoragePath(hash);

            // When
            Resource resource = storageEngine.retrieveRange(path, 2, 2);

            // Then
            try (InputStream is = resource.getInputStream()) {
                byte[] retrieved = is.readAllBytes();
                assertThat(new String(retrieved)).isEqualTo("C");
            }
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("should delete stored content")
        void shouldDeleteStoredContent() throws IOException {
            // Given
            byte[] content = "Content to delete".getBytes();
            String hash = storageEngine.store(new ByteArrayInputStream(content));
            String path = storageEngine.getStoragePath(hash);

            Path fullPath = storagePath.resolve(path);
            assertThat(Files.exists(fullPath)).isTrue();

            // When
            storageEngine.delete(path);

            // Then
            assertThat(Files.exists(fullPath)).isFalse();
        }

        @Test
        @DisplayName("should not throw exception when deleting non-existent file")
        void shouldNotThrowWhenDeletingNonExistent() {
            // Given
            String nonExistentPath = "ab/cd/nonexistent";

            // When & Then
            assertThatCode(() -> storageEngine.delete(nonExistentPath))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("exists")
    class ExistsTests {

        @Test
        @DisplayName("should return true for existing content")
        void shouldReturnTrueForExistingContent() throws IOException {
            // Given
            byte[] content = "Existing content".getBytes();
            String hash = storageEngine.store(new ByteArrayInputStream(content));
            String path = storageEngine.getStoragePath(hash);

            // When
            boolean exists = storageEngine.exists(path);

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false for non-existing content")
        void shouldReturnFalseForNonExistingContent() {
            // Given
            String path = "ab/cd/nonexistent";

            // When
            boolean exists = storageEngine.exists(path);

            // Then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("getStoragePath")
    class GetStoragePathTests {

        @Test
        @DisplayName("should generate correct 2-level directory structure")
        void shouldGenerateCorrectDirectoryStructure() {
            // Given
            String hash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";

            // When
            String path = storageEngine.getStoragePath(hash);

            // Then
            assertThat(path).isEqualTo("ab/cd/" + hash);
        }
    }

    @Nested
    @DisplayName("concatenate")
    class ConcatenateTests {

        @Test
        @DisplayName("should concatenate multiple parts into single file")
        void shouldConcatenateMultipleParts() throws IOException {
            // Given
            byte[] part1 = "Part1-".getBytes();
            byte[] part2 = "Part2-".getBytes();
            byte[] part3 = "Part3".getBytes();

            String hash1 = storageEngine.store(new ByteArrayInputStream(part1));
            String hash2 = storageEngine.store(new ByteArrayInputStream(part2));
            String hash3 = storageEngine.store(new ByteArrayInputStream(part3));

            List<String> partPaths = Arrays.asList(
                    storageEngine.getStoragePath(hash1),
                    storageEngine.getStoragePath(hash2),
                    storageEngine.getStoragePath(hash3));

            // When
            String finalHash = storageEngine.concatenate(partPaths);

            // Then
            assertThat(finalHash).isNotNull();

            String finalPath = storageEngine.getStoragePath(finalHash);
            Resource resource = storageEngine.retrieve(finalPath);

            try (InputStream is = resource.getInputStream()) {
                String content = new String(is.readAllBytes());
                assertThat(content).isEqualTo("Part1-Part2-Part3");
            }
        }

        @Test
        @DisplayName("should handle single part concatenation")
        void shouldHandleSinglePartConcatenation() throws IOException {
            // Given
            byte[] part = "Single part".getBytes();
            String hash = storageEngine.store(new ByteArrayInputStream(part));
            List<String> partPaths = Arrays.asList(storageEngine.getStoragePath(hash));

            // When
            String finalHash = storageEngine.concatenate(partPaths);

            // Then
            assertThat(finalHash).isEqualTo(hash); // Same content = same hash
        }
    }

    @Nested
    @DisplayName("getSize")
    class GetSizeTests {

        @Test
        @DisplayName("should return correct file size")
        void shouldReturnCorrectFileSize() throws IOException {
            // Given
            byte[] content = "Test content with known size".getBytes();
            String hash = storageEngine.store(new ByteArrayInputStream(content));
            String path = storageEngine.getStoragePath(hash);

            // When
            long size = storageEngine.getSize(path);

            // Then
            assertThat(size).isEqualTo(content.length);
        }
    }

    @Nested
    @DisplayName("createTempFile / deleteTempFile")
    class TempFileTests {

        @Test
        @DisplayName("should create and delete temp file")
        void shouldCreateAndDeleteTempFile() throws IOException {
            // Given
            byte[] content = "Temporary content".getBytes();

            // When
            Path tempFile = storageEngine.createTempFile(new ByteArrayInputStream(content));

            // Then
            assertThat(Files.exists(tempFile)).isTrue();
            assertThat(Files.readAllBytes(tempFile)).isEqualTo(content);

            // Cleanup
            storageEngine.deleteTempFile(tempFile);
            assertThat(Files.exists(tempFile)).isFalse();
        }
    }

    private String calculateSha256(byte[] content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);
        return HexFormat.of().formatHex(hash);
    }
}
