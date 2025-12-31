package com.kafka.storage;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LogSegment represents a single segment file in a partition's log.
 * 
 * Each segment consists of:
 * - .log file: Contains the actual records
 * - .index file: Sparse offset index for fast lookups
 * - .timeindex file: Timestamp to offset mapping
 * 
 * Segment naming: {baseOffset}.{extension}
 * E.g., 00000000000000000000.log for segment starting at offset 0
 * 
 * Index entries are sparse (one per indexIntervalBytes) for space efficiency.
 * Binary search on index, then sequential scan to find exact offset.
 * 
 * Segment lifecycle:
 * 1. Active: Accepting new writes
 * 2. Rolled: No longer accepting writes (size/time limit reached)
 * 3. Deleted: Removed by retention policy
 */
@Slf4j
@Getter
public class LogSegment implements Closeable {

    private static final int INDEX_ENTRY_SIZE = 8; // 4 bytes relative offset + 4 bytes position
    private static final int TIME_INDEX_ENTRY_SIZE = 12; // 8 bytes timestamp + 4 bytes relative offset

    private final Path logFile;
    private final Path indexFile;
    private final Path timeIndexFile;
    private final long baseOffset;
    private final int indexIntervalBytes;
    private final int maxSegmentBytes;
    private final int maxIndexBytes;

    private FileChannel logChannel;
    private MappedByteBuffer indexBuffer;
    private MappedByteBuffer timeIndexBuffer;

    private final AtomicLong logSize = new AtomicLong(0);
    private final AtomicLong nextOffset;
    private int indexEntries = 0;
    private int timeIndexEntries = 0;
    private int bytesSinceLastIndex = 0;
    private long maxTimestamp = 0;
    private long offsetOfMaxTimestamp = 0;

    public LogSegment(Path dir, long baseOffset, int maxSegmentBytes, int maxIndexBytes, int indexIntervalBytes)
            throws IOException {
        this.baseOffset = baseOffset;
        this.maxSegmentBytes = maxSegmentBytes;
        this.maxIndexBytes = maxIndexBytes;
        this.indexIntervalBytes = indexIntervalBytes;
        this.nextOffset = new AtomicLong(baseOffset);

        String segmentName = String.format("%020d", baseOffset);
        this.logFile = dir.resolve(segmentName + ".log");
        this.indexFile = dir.resolve(segmentName + ".index");
        this.timeIndexFile = dir.resolve(segmentName + ".timeindex");

        open();
    }

    private void open() throws IOException {
        // Open log file
        logChannel = FileChannel.open(logFile,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        logSize.set(logChannel.size());

        // Memory-map index files
        try (RandomAccessFile raf = new RandomAccessFile(indexFile.toFile(), "rw")) {
            raf.setLength(maxIndexBytes);
            indexBuffer = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, maxIndexBytes);
        }

        try (RandomAccessFile raf = new RandomAccessFile(timeIndexFile.toFile(), "rw")) {
            raf.setLength(maxIndexBytes);
            timeIndexBuffer = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, maxIndexBytes);
        }

        // Recover state if files already exist
        if (logSize.get() > 0) {
            recover();
        }
    }

    /**
     * Recover segment state from existing files.
     */
    private void recover() throws IOException {
        log.info("[SEGMENT] Recovering segment {} from {} bytes", baseOffset, logSize.get());

        ByteBuffer buffer = ByteBuffer.allocate((int) logSize.get());
        logChannel.read(buffer, 0);
        buffer.flip();

        long lastOffset = baseOffset - 1;
        while (buffer.hasRemaining()) {
            try {
                RecordBatch batch = RecordBatch.deserialize(buffer);
                lastOffset = batch.lastOffset();
                maxTimestamp = Math.max(maxTimestamp, batch.getMaxTimestamp());
            } catch (Exception e) {
                log.warn("[SEGMENT] Error during recovery at position {}: {}", buffer.position(), e.getMessage());
                break;
            }
        }

        nextOffset.set(lastOffset + 1);

        // Rebuild indexes
        rebuildIndex();

        log.info("[SEGMENT] Recovered segment {}, next offset: {}", baseOffset, nextOffset.get());
    }

    /**
     * Append a record batch to this segment.
     * 
     * @return The offset of the first record in the batch
     */
    public synchronized long append(RecordBatch batch) throws IOException {
        long offset = nextOffset.get();
        batch.setBaseOffset(offset);

        byte[] data = batch.serialize();
        int position = (int) logSize.get();

        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            logChannel.write(buffer, position + data.length - buffer.remaining());
        }

        // Update state
        logSize.addAndGet(data.length);
        bytesSinceLastIndex += data.length;
        nextOffset.addAndGet(batch.getRecords().size());

        // Update timestamp
        if (batch.getMaxTimestamp() > maxTimestamp) {
            maxTimestamp = batch.getMaxTimestamp();
            offsetOfMaxTimestamp = batch.lastOffset();
        }

        // Add index entry if threshold reached
        if (bytesSinceLastIndex >= indexIntervalBytes) {
            appendIndexEntry(offset, position);
            appendTimeIndexEntry(batch.getFirstTimestamp(), offset);
            bytesSinceLastIndex = 0;
        }

        log.debug("[SEGMENT] Appended batch at offset {}, size {} bytes", offset, data.length);
        return offset;
    }

    /**
     * Read records starting from the given offset.
     * 
     * @param startOffset The offset to start reading from
     * @param maxBytes    Maximum bytes to read
     * @return List of record batches
     */
    public List<RecordBatch> read(long startOffset, int maxBytes) throws IOException {
        List<RecordBatch> batches = new ArrayList<>();

        // Find position using index
        int position = findPosition(startOffset);
        if (position < 0) {
            return batches;
        }

        int bytesRead = 0;
        ByteBuffer buffer = ByteBuffer.allocate(maxBytes);

        int read = logChannel.read(buffer, position);
        buffer.flip();

        while (buffer.hasRemaining() && bytesRead < maxBytes) {
            int batchStart = buffer.position();
            try {
                RecordBatch batch = RecordBatch.deserialize(buffer);
                if (batch.getBaseOffset() >= startOffset) {
                    batches.add(batch);
                    bytesRead += buffer.position() - batchStart;
                }
            } catch (Exception e) {
                log.debug("[SEGMENT] End of readable data at position {}", buffer.position());
                break;
            }
        }

        return batches;
    }

    /**
     * Find the file position for a given offset using the index.
     */
    private int findPosition(long targetOffset) {
        if (targetOffset < baseOffset) {
            return -1;
        }
        if (targetOffset >= nextOffset.get()) {
            return -1;
        }

        // Binary search in index
        int relativeOffset = (int) (targetOffset - baseOffset);
        int lo = 0;
        int hi = indexEntries - 1;
        int foundPosition = 0;

        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            int entryOffset = indexBuffer.getInt(mid * INDEX_ENTRY_SIZE);
            int entryPosition = indexBuffer.getInt(mid * INDEX_ENTRY_SIZE + 4);

            if (entryOffset <= relativeOffset) {
                foundPosition = entryPosition;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }

        return foundPosition;
    }

    /**
     * Find offset for a given timestamp.
     */
    public long findOffsetByTimestamp(long timestamp) {
        // Binary search in time index
        int lo = 0;
        int hi = timeIndexEntries - 1;
        int foundRelativeOffset = 0;

        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            long entryTimestamp = timeIndexBuffer.getLong(mid * TIME_INDEX_ENTRY_SIZE);
            int entryRelativeOffset = timeIndexBuffer.getInt(mid * TIME_INDEX_ENTRY_SIZE + 8);

            if (entryTimestamp <= timestamp) {
                foundRelativeOffset = entryRelativeOffset;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }

        return baseOffset + foundRelativeOffset;
    }

    private void appendIndexEntry(long offset, int position) {
        if (indexEntries * INDEX_ENTRY_SIZE >= maxIndexBytes - INDEX_ENTRY_SIZE) {
            log.warn("[SEGMENT] Index full for segment {}", baseOffset);
            return;
        }

        int relativeOffset = (int) (offset - baseOffset);
        indexBuffer.putInt(indexEntries * INDEX_ENTRY_SIZE, relativeOffset);
        indexBuffer.putInt(indexEntries * INDEX_ENTRY_SIZE + 4, position);
        indexEntries++;
    }

    private void appendTimeIndexEntry(long timestamp, long offset) {
        if (timeIndexEntries * TIME_INDEX_ENTRY_SIZE >= maxIndexBytes - TIME_INDEX_ENTRY_SIZE) {
            return;
        }

        int relativeOffset = (int) (offset - baseOffset);
        timeIndexBuffer.putLong(timeIndexEntries * TIME_INDEX_ENTRY_SIZE, timestamp);
        timeIndexBuffer.putInt(timeIndexEntries * TIME_INDEX_ENTRY_SIZE + 8, relativeOffset);
        timeIndexEntries++;
    }

    private void rebuildIndex() throws IOException {
        indexEntries = 0;
        timeIndexEntries = 0;
        bytesSinceLastIndex = 0;

        indexBuffer.clear();
        timeIndexBuffer.clear();

        ByteBuffer buffer = ByteBuffer.allocate((int) logSize.get());
        logChannel.read(buffer, 0);
        buffer.flip();

        int position = 0;
        while (buffer.hasRemaining()) {
            int batchStart = buffer.position();
            try {
                RecordBatch batch = RecordBatch.deserialize(buffer);
                int batchSize = buffer.position() - batchStart;
                bytesSinceLastIndex += batchSize;

                if (bytesSinceLastIndex >= indexIntervalBytes) {
                    appendIndexEntry(batch.getBaseOffset(), batchStart);
                    appendTimeIndexEntry(batch.getFirstTimestamp(), batch.getBaseOffset());
                    bytesSinceLastIndex = 0;
                }

                position = buffer.position();
            } catch (Exception e) {
                break;
            }
        }
    }

    /**
     * Check if segment has room for more data.
     */
    public boolean hasSpace(int bytes) {
        return logSize.get() + bytes <= maxSegmentBytes;
    }

    /**
     * Check if segment should be rolled based on time.
     */
    public boolean shouldRoll(long maxSegmentMs) {
        if (maxTimestamp == 0)
            return false;
        return System.currentTimeMillis() - maxTimestamp > maxSegmentMs;
    }

    /**
     * Flush data to disk.
     */
    public void flush() throws IOException {
        logChannel.force(true);
        indexBuffer.force();
        timeIndexBuffer.force();
    }

    /**
     * Get the size of this segment in bytes.
     */
    public long size() {
        return logSize.get();
    }

    /**
     * Delete this segment.
     */
    public void delete() throws IOException {
        close();
        Files.deleteIfExists(logFile);
        Files.deleteIfExists(indexFile);
        Files.deleteIfExists(timeIndexFile);
        log.info("[SEGMENT] Deleted segment {}", baseOffset);
    }

    @Override
    public void close() throws IOException {
        if (logChannel != null && logChannel.isOpen()) {
            logChannel.close();
        }
        // MappedByteBuffer doesn't need explicit closing but we can help GC
        indexBuffer = null;
        timeIndexBuffer = null;
    }
}
