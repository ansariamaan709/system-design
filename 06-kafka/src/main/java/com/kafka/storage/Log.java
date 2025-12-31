package com.kafka.storage;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Log represents the complete log for a single partition.
 * 
 * The log is an append-only sequence of record batches stored in segment files.
 * 
 * Key responsibilities:
 * - Segment management (rolling, cleanup)
 * - Append operations with proper offset assignment
 * - Read operations with offset lookup
 * - Retention enforcement (time-based and size-based)
 * - Recovery on startup
 * 
 * Structure:
 * log/
 * └── topic-partition/
 * ├── 00000000000000000000.log
 * ├── 00000000000000000000.index
 * ├── 00000000000000000000.timeindex
 * ├── 00000000000000123456.log
 * ├── 00000000000000123456.index
 * └── 00000000000000123456.timeindex
 */
@Slf4j
@Getter
public class Log implements Closeable {

    private final String topicPartition;
    private final Path logDir;
    private final LogConfig config;

    // Segments ordered by base offset
    private final ConcurrentNavigableMap<Long, LogSegment> segments = new ConcurrentSkipListMap<>();
    private volatile LogSegment activeSegment;

    // Log state
    private final AtomicLong logEndOffset = new AtomicLong(0); // LEO
    private final AtomicLong logStartOffset = new AtomicLong(0);
    private volatile long highWatermark = 0; // HW

    // Concurrency control
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Object appendLock = new Object();

    /**
     * Configuration for the log.
     */
    @Getter
    public static class LogConfig {
        private final int segmentBytes; // Max bytes per segment
        private final long segmentMs; // Max age before rolling
        private final int indexIntervalBytes; // Index entry interval
        private final int maxIndexBytes; // Max index file size
        private final long retentionMs; // How long to keep data
        private final long retentionBytes; // Max total log size
        private final boolean compactionEnabled; // Log compaction

        public LogConfig() {
            this(1073741824, 604800000L, 4096, 10485760, 604800000L, -1L, false);
        }

        public LogConfig(int segmentBytes, long segmentMs, int indexIntervalBytes,
                int maxIndexBytes, long retentionMs, long retentionBytes,
                boolean compactionEnabled) {
            this.segmentBytes = segmentBytes;
            this.segmentMs = segmentMs;
            this.indexIntervalBytes = indexIntervalBytes;
            this.maxIndexBytes = maxIndexBytes;
            this.retentionMs = retentionMs;
            this.retentionBytes = retentionBytes;
            this.compactionEnabled = compactionEnabled;
        }
    }

    public Log(String topicPartition, Path logDir, LogConfig config) throws IOException {
        this.topicPartition = topicPartition;
        this.logDir = logDir;
        this.config = config;

        // Create directory if needed
        Files.createDirectories(logDir);

        // Load existing segments
        loadSegments();

        // Ensure we have an active segment
        if (segments.isEmpty()) {
            roll(0);
        } else {
            activeSegment = segments.lastEntry().getValue();
            logEndOffset.set(activeSegment.getNextOffset().get());
            logStartOffset.set(segments.firstKey());
        }

        log.info("[LOG] Initialized {} with {} segments, LEO={}, start={}",
                topicPartition, segments.size(), logEndOffset.get(), logStartOffset.get());
    }

    /**
     * Load existing segments from disk.
     */
    private void loadSegments() throws IOException {
        try (Stream<Path> files = Files.list(logDir)) {
            List<Long> baseOffsets = files
                    .filter(p -> p.toString().endsWith(".log"))
                    .map(p -> {
                        String name = p.getFileName().toString();
                        return Long.parseLong(name.substring(0, 20));
                    })
                    .sorted()
                    .collect(Collectors.toList());

            for (Long baseOffset : baseOffsets) {
                LogSegment segment = new LogSegment(
                        logDir, baseOffset, config.segmentBytes,
                        config.maxIndexBytes, config.indexIntervalBytes);
                segments.put(baseOffset, segment);
            }
        }
    }

    /**
     * Append records to the log.
     * 
     * @param records Records to append
     * @return LogAppendInfo with offsets assigned
     */
    public LogAppendInfo append(List<Record> records) throws IOException {
        if (records.isEmpty()) {
            return new LogAppendInfo(logEndOffset.get(), logEndOffset.get(), 0);
        }

        synchronized (appendLock) {
            // Check if we need to roll segment
            int estimatedSize = records.stream().mapToInt(Record::sizeInBytes).sum();
            if (!activeSegment.hasSpace(estimatedSize)) {
                roll(logEndOffset.get());
            }

            // Assign offsets
            long firstOffset = logEndOffset.get();
            long timestamp = System.currentTimeMillis();

            for (int i = 0; i < records.size(); i++) {
                Record record = records.get(i);
                record.setOffset(firstOffset + i);
                if (record.getTimestamp() == 0) {
                    record.setTimestamp(timestamp);
                }
            }

            // Create batch
            RecordBatch batch = RecordBatch.builder()
                    .baseOffset(firstOffset)
                    .firstTimestamp(records.get(0).getTimestamp())
                    .maxTimestamp(records.stream().mapToLong(Record::getTimestamp).max().orElse(timestamp))
                    .producerId(records.get(0).getProducerId() != null ? records.get(0).getProducerId() : -1L)
                    .producerEpoch(
                            records.get(0).getProducerEpoch() != null ? records.get(0).getProducerEpoch() : (short) -1)
                    .baseSequence(records.get(0).getSequence() != null ? records.get(0).getSequence() : -1)
                    .records(records)
                    .build();

            // Append to segment
            activeSegment.append(batch);

            // Update LEO
            long lastOffset = firstOffset + records.size() - 1;
            logEndOffset.set(lastOffset + 1);

            log.debug("[LOG] Appended {} records to {}, offsets [{}, {}]",
                    records.size(), topicPartition, firstOffset, lastOffset);

            return new LogAppendInfo(firstOffset, lastOffset, batch.sizeInBytes());
        }
    }

    /**
     * Read records starting from the given offset.
     * 
     * @param startOffset Starting offset
     * @param maxBytes    Maximum bytes to read
     * @return FetchResult with records
     */
    public FetchResult read(long startOffset, int maxBytes) throws IOException {
        lock.readLock().lock();
        try {
            if (startOffset < logStartOffset.get()) {
                return new FetchResult(Collections.emptyList(), logStartOffset.get(),
                        FetchResult.Error.OFFSET_OUT_OF_RANGE);
            }

            if (startOffset >= logEndOffset.get()) {
                return new FetchResult(Collections.emptyList(), logEndOffset.get(), null);
            }

            // Find the segment containing the offset
            Map.Entry<Long, LogSegment> entry = segments.floorEntry(startOffset);
            if (entry == null) {
                return new FetchResult(Collections.emptyList(), logStartOffset.get(),
                        FetchResult.Error.OFFSET_OUT_OF_RANGE);
            }

            LogSegment segment = entry.getValue();
            List<RecordBatch> batches = segment.read(startOffset, maxBytes);

            // Flatten to records
            List<Record> records = batches.stream()
                    .flatMap(b -> b.getRecords().stream())
                    .filter(r -> r.getOffset() >= startOffset && r.getOffset() < highWatermark)
                    .collect(Collectors.toList());

            long nextOffset = records.isEmpty() ? startOffset : records.get(records.size() - 1).getOffset() + 1;

            return new FetchResult(records, nextOffset, null);

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Roll to a new segment.
     */
    public void roll(long newBaseOffset) throws IOException {
        lock.writeLock().lock();
        try {
            if (activeSegment != null) {
                activeSegment.flush();
            }

            LogSegment newSegment = new LogSegment(
                    logDir, newBaseOffset, config.segmentBytes,
                    config.maxIndexBytes, config.indexIntervalBytes);

            segments.put(newBaseOffset, newSegment);
            activeSegment = newSegment;

            log.info("[LOG] Rolled to new segment at offset {} for {}", newBaseOffset, topicPartition);

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update high watermark.
     */
    public void updateHighWatermark(long hw) {
        if (hw > this.highWatermark && hw <= logEndOffset.get()) {
            this.highWatermark = hw;
        }
    }

    /**
     * Delete segments older than retention time.
     */
    public int deleteOldSegments() throws IOException {
        long now = System.currentTimeMillis();
        long cutoffTime = now - config.retentionMs;

        int deleted = 0;
        lock.writeLock().lock();
        try {
            Iterator<Map.Entry<Long, LogSegment>> iter = segments.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Long, LogSegment> entry = iter.next();
                LogSegment segment = entry.getValue();

                // Don't delete the active segment
                if (segment == activeSegment) {
                    continue;
                }

                // Check age
                if (segment.getMaxTimestamp() > 0 && segment.getMaxTimestamp() < cutoffTime) {
                    segment.delete();
                    iter.remove();
                    deleted++;
                    log.info("[LOG] Deleted segment {} due to retention", entry.getKey());
                }
            }

            // Update log start offset
            if (!segments.isEmpty()) {
                logStartOffset.set(segments.firstKey());
            }

        } finally {
            lock.writeLock().unlock();
        }

        return deleted;
    }

    /**
     * Delete segments to fit within size limit.
     */
    public int deleteSegmentsToFitSize() throws IOException {
        if (config.retentionBytes < 0) {
            return 0;
        }

        int deleted = 0;
        lock.writeLock().lock();
        try {
            long totalSize = segments.values().stream().mapToLong(LogSegment::size).sum();

            Iterator<Map.Entry<Long, LogSegment>> iter = segments.entrySet().iterator();
            while (iter.hasNext() && totalSize > config.retentionBytes) {
                Map.Entry<Long, LogSegment> entry = iter.next();
                LogSegment segment = entry.getValue();

                if (segment == activeSegment) {
                    break;
                }

                totalSize -= segment.size();
                segment.delete();
                iter.remove();
                deleted++;
            }

            if (!segments.isEmpty()) {
                logStartOffset.set(segments.firstKey());
            }

        } finally {
            lock.writeLock().unlock();
        }

        return deleted;
    }

    /**
     * Get total log size in bytes.
     */
    public long size() {
        return segments.values().stream().mapToLong(LogSegment::size).sum();
    }

    /**
     * Flush all segments to disk.
     */
    public void flush() throws IOException {
        for (LogSegment segment : segments.values()) {
            segment.flush();
        }
    }

    /**
     * Find offset for a given timestamp.
     */
    public long findOffsetByTimestamp(long timestamp) {
        for (LogSegment segment : segments.values()) {
            long offset = segment.findOffsetByTimestamp(timestamp);
            if (offset >= 0) {
                return offset;
            }
        }
        return logEndOffset.get();
    }

    /**
     * Truncate log to a specific offset.
     * Removes all records at or after the given offset.
     */
    public void truncateTo(long targetOffset) throws IOException {
        lock.writeLock().lock();
        try {
            if (targetOffset >= logEndOffset.get()) {
                return; // Nothing to truncate
            }

            if (targetOffset < logStartOffset.get()) {
                throw new IllegalArgumentException(
                        "Cannot truncate to offset " + targetOffset + " which is before log start " + logStartOffset.get());
            }

            // Find and remove segments that are entirely after targetOffset
            Iterator<Map.Entry<Long, LogSegment>> iter = segments.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Long, LogSegment> entry = iter.next();
                if (entry.getKey() >= targetOffset) {
                    entry.getValue().delete();
                    iter.remove();
                }
            }

            // Update active segment
            if (!segments.isEmpty()) {
                activeSegment = segments.lastEntry().getValue();
            } else {
                roll(logStartOffset.get());
            }

            // Update log end offset
            logEndOffset.set(targetOffset);

            // Update high watermark if needed
            if (highWatermark > targetOffset) {
                highWatermark = targetOffset;
            }

            log.info("[LOG] Truncated {} to offset {}", topicPartition, targetOffset);

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        for (LogSegment segment : segments.values()) {
            segment.close();
        }
    }

    /**
     * Result of append operation.
     */
    @lombok.Value
    public static class LogAppendInfo {
        long firstOffset;
        long lastOffset;
        int bytesWritten;
    }

    /**
     * Result of fetch operation.
     */
    @lombok.Value
    public static class FetchResult {
        List<Record> records;
        long nextOffset;
        Error error;

        public enum Error {
            OFFSET_OUT_OF_RANGE,
            NOT_LEADER_FOR_PARTITION
        }
    }
}
