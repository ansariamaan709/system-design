package com.kafka.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Log storage component.
 */
class LogTest {

    @TempDir
    Path tempDir;

    private Log log;

    @BeforeEach
    void setUp() throws IOException {
        Log.LogConfig config = new Log.LogConfig();
        log = new Log("test-topic-0", tempDir, config);
    }

    @Test
    void shouldStartWithZeroOffsets() {
        assertEquals(0, log.getLogStartOffset().get());
        assertEquals(0, log.getLogEndOffset().get());
        assertEquals(0, log.getHighWatermark());
    }

    @Test
    void shouldAppendRecordBatch() throws IOException {
        List<Record> records = createTestRecords();
        Log.LogAppendInfo info = log.append(records);

        assertEquals(0, info.getFirstOffset());
        assertTrue(log.getLogEndOffset().get() > 0);
    }

    @Test
    void shouldReadAppendedRecords() throws IOException {
        // Append a batch
        List<Record> records = createTestRecords();
        log.append(records);

        // Update high watermark to make records visible
        log.updateHighWatermark(log.getLogEndOffset().get());

        // Read records
        Log.FetchResult result = log.read(0, 1024 * 1024);

        assertFalse(result.getRecords().isEmpty());
    }

    @Test
    void shouldAssignSequentialOffsets() throws IOException {
        // Append multiple batches
        for (int i = 0; i < 5; i++) {
            List<Record> records = createTestRecords();
            Log.LogAppendInfo info = log.append(records);
            assertEquals(i, info.getFirstOffset());
        }

        assertEquals(5, log.getLogEndOffset().get());
    }

    @Test
    void shouldTruncateToOffset() throws IOException {
        // Append batches
        for (int i = 0; i < 5; i++) {
            log.append(createTestRecords());
        }

        // Truncate to offset 3
        log.truncateTo(3);

        assertEquals(3, log.getLogEndOffset().get());
    }

    @Test
    void shouldRespectHighWatermark() throws IOException {
        // Append records
        log.append(createTestRecords());
        log.append(createTestRecords());

        // High watermark is 0, should return empty
        Log.FetchResult result = log.read(0, 1024 * 1024);
        assertTrue(result.getRecords().isEmpty());

        // Update high watermark
        log.updateHighWatermark(log.getLogEndOffset().get());

        // Now should return records
        result = log.read(0, 1024 * 1024);
        assertFalse(result.getRecords().isEmpty());
    }

    @Test
    void shouldReportCorrectSize() throws IOException {
        assertEquals(0, log.size());

        log.append(createTestRecords());

        assertTrue(log.size() > 0);
    }

    @Test
    void shouldFlushToDisk() throws IOException {
        log.append(createTestRecords());

        // Should not throw
        assertDoesNotThrow(() -> log.flush());
    }

    @Test
    void shouldCloseCleanly() throws IOException {
        log.append(createTestRecords());

        assertDoesNotThrow(() -> log.close());
    }

    private List<Record> createTestRecords() {
        Record record = Record.builder()
                .offset(0)
                .timestamp(System.currentTimeMillis())
                .key("key".getBytes())
                .value("value".getBytes())
                .build();

        return List.of(record);
    }
}
