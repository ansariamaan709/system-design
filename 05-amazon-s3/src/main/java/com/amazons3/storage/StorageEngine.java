package com.amazons3.storage;

import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Content-Addressable Storage Engine.
 * 
 * Stores objects using content hash as the storage key, enabling:
 * - Automatic deduplication
 * - Data integrity verification
 * - Efficient storage utilization
 * 
 * Storage layout:
 * {root}/
 * {hash[0:2]}/
 * {hash[2:4]}/
 * {hash}
 */
@Slf4j
@Component
public class StorageEngine {

    @Value("${storage.root-path:./data/objects}")
    private String rootPath;

    @Value("${storage.temp-path:./data/temp}")
    private String tempPath;

    @Value("${storage.chunk-size-bytes:5242880}")
    private int chunkSize; // 5MB default

    private Path rootDir;
    private Path tempDir;

    @PostConstruct
    public void init() throws IOException {
        rootDir = Paths.get(rootPath);
        tempDir = Paths.get(tempPath);

        Files.createDirectories(rootDir);
        Files.createDirectories(tempDir);

        log.info("[STORAGE] Initialized storage engine at {}", rootDir.toAbsolutePath());
    }

    /**
     * Store data from InputStream and return storage info.
     * Uses content-addressable storage with SHA-256 hash.
     */
    public StorageResult store(InputStream inputStream) throws IOException {
        // Write to temp file while computing hash
        Path tempFile = createTempFile();

        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

            long size = 0;
            byte[] buffer = new byte[chunkSize];
            int bytesRead;

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    md5.update(buffer, 0, bytesRead);
                    sha256.update(buffer, 0, bytesRead);
                    size += bytesRead;
                }
            }

            String md5Hash = HexFormat.of().formatHex(md5.digest());
            String sha256Hash = HexFormat.of().formatHex(sha256.digest());
            String etag = "\"" + md5Hash + "\"";

            // Determine storage path based on content hash
            String storagePath = getStoragePath(sha256Hash);
            Path targetPath = rootDir.resolve(storagePath);

            // Check for existing content (deduplication)
            if (Files.exists(targetPath)) {
                // Content already exists, delete temp file
                Files.delete(tempFile);
                log.debug("[STORAGE] Deduplicated content: {}", sha256Hash);
            } else {
                // Move temp file to permanent location
                Files.createDirectories(targetPath.getParent());
                Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE);
            }

            return StorageResult.builder()
                    .storagePath(storagePath)
                    .etag(etag)
                    .checksumSha256(sha256Hash)
                    .sizeBytes(size)
                    .build();

        } catch (Exception e) {
            // Clean up temp file on error
            Files.deleteIfExists(tempFile);
            throw new IOException("Failed to store object", e);
        }
    }

    /**
     * Store data from byte array.
     */
    public StorageResult store(byte[] data) throws IOException {
        return store(new ByteArrayInputStream(data));
    }

    /**
     * Retrieve object data as InputStream.
     */
    public InputStream retrieve(String storagePath) throws IOException {
        Path path = rootDir.resolve(storagePath);

        if (!Files.exists(path)) {
            throw new FileNotFoundException("Object not found: " + storagePath);
        }

        return new BufferedInputStream(Files.newInputStream(path));
    }

    /**
     * Retrieve object data with range support.
     */
    public InputStream retrieveRange(String storagePath, long start, long end) throws IOException {
        Path path = rootDir.resolve(storagePath);

        if (!Files.exists(path)) {
            throw new FileNotFoundException("Object not found: " + storagePath);
        }

        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        raf.seek(start);

        long length = end - start + 1;
        return new BoundedInputStream(raf, length);
    }

    /**
     * Delete object from storage.
     * Returns true if deleted, false if not found.
     */
    public boolean delete(String storagePath) throws IOException {
        Path path = rootDir.resolve(storagePath);
        return Files.deleteIfExists(path);
    }

    /**
     * Check if object exists in storage.
     */
    public boolean exists(String storagePath) {
        return Files.exists(rootDir.resolve(storagePath));
    }

    /**
     * Get object size.
     */
    public long getSize(String storagePath) throws IOException {
        Path path = rootDir.resolve(storagePath);
        return Files.size(path);
    }

    /**
     * Copy object within storage.
     */
    public String copy(String sourceStoragePath) throws IOException {
        // Content-addressable storage means copy is just reference
        // The same content hash = same storage path
        return sourceStoragePath;
    }

    /**
     * Create temp file for upload.
     */
    public Path createTempFile() throws IOException {
        return Files.createTempFile(tempDir, "upload-", ".tmp");
    }

    /**
     * Get storage path from content hash.
     * Uses 2-level directory structure for better filesystem performance.
     */
    private String getStoragePath(String hash) {
        return hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash;
    }

    /**
     * Calculate storage statistics.
     */
    public StorageStats getStats() throws IOException {
        long totalSize = 0;
        long fileCount = 0;

        try (var walk = Files.walk(rootDir)) {
            var files = walk.filter(Files::isRegularFile).toList();
            fileCount = files.size();
            for (Path file : files) {
                totalSize += Files.size(file);
            }
        }

        return new StorageStats(fileCount, totalSize);
    }

    /**
     * Clean up orphaned temp files.
     */
    public int cleanupTempFiles(long olderThanMillis) throws IOException {
        int deleted = 0;
        long threshold = System.currentTimeMillis() - olderThanMillis;

        try (var stream = Files.list(tempDir)) {
            for (Path file : stream.toList()) {
                if (Files.getLastModifiedTime(file).toMillis() < threshold) {
                    Files.deleteIfExists(file);
                    deleted++;
                }
            }
        }

        return deleted;
    }

    /**
     * Storage result containing path, etag, and checksums.
     */
    @lombok.Value
    @lombok.Builder
    public static class StorageResult {
        String storagePath;
        String etag;
        String checksumSha256;
        long sizeBytes;
    }

    /**
     * Storage statistics.
     */
    public record StorageStats(long fileCount, long totalSizeBytes) {
    }

    /**
     * Bounded input stream for range requests.
     */
    private static class BoundedInputStream extends InputStream {
        private final RandomAccessFile raf;
        private long remaining;

        BoundedInputStream(RandomAccessFile raf, long length) {
            this.raf = raf;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            remaining--;
            return raf.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int read = raf.read(b, off, toRead);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }
    }
}
