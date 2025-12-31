package com.kafka.storage;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * RecordBatch represents a batch of records for efficient I/O.
 * 
 * Kafka batches records for:
 * - Reduced network overhead (single request for multiple records)
 * - Better compression (compress batch, not individual records)
 * - Efficient disk I/O (sequential writes)
 * 
 * Batch header layout (61 bytes):
 * - Base Offset (8 bytes) - First offset in batch
 * - Batch Length (4 bytes) - Size after this field
 * - Partition Leader Epoch (4 bytes)
 * - Magic (1 byte) - Format version (2 for current)
 * - CRC (4 bytes) - Checksum of batch from attributes to end
 * - Attributes (2 bytes) - Compression, timestamp type, txn, control
 * - Last Offset Delta (4 bytes) - Difference from base to last offset
 * - First Timestamp (8 bytes)
 * - Max Timestamp (8 bytes)
 * - Producer ID (8 bytes)
 * - Producer Epoch (2 bytes)
 * - Base Sequence (4 bytes)
 * - Records Count (4 bytes)
 * 
 * Followed by serialized records.
 */
@Slf4j
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordBatch {

    public static final byte MAGIC_VALUE = 2;
    public static final int HEADER_SIZE = 61;

    private long baseOffset;
    private int partitionLeaderEpoch;
    private long firstTimestamp;
    private long maxTimestamp;
    private long producerId;
    private short producerEpoch;
    private int baseSequence;

    @Builder.Default
    private CompressionType compressionType = CompressionType.NONE;

    @Builder.Default
    private TimestampType timestampType = TimestampType.CREATE_TIME;

    private boolean isTransactional;
    private boolean isControlBatch;

    @Builder.Default
    private List<Record> records = new ArrayList<>();

    public enum CompressionType {
        NONE(0),
        GZIP(1),
        SNAPPY(2),
        LZ4(3),
        ZSTD(4);

        private final int id;

        CompressionType(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static CompressionType fromId(int id) {
            for (CompressionType type : values()) {
                if (type.id == id)
                    return type;
            }
            return NONE;
        }
    }

    public enum TimestampType {
        CREATE_TIME(0),
        LOG_APPEND_TIME(1);

        private final int id;

        TimestampType(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }
    }

    /**
     * Serialize batch to bytes.
     */
    public byte[] serialize() {
        // First, serialize all records
        List<byte[]> serializedRecords = new ArrayList<>();
        int recordsSize = 0;
        for (Record record : records) {
            byte[] serialized = record.serialize();
            serializedRecords.add(serialized);
            recordsSize += serialized.length;
        }

        // Compress if needed
        byte[] recordsData = new byte[recordsSize];
        int pos = 0;
        for (byte[] data : serializedRecords) {
            System.arraycopy(data, 0, recordsData, pos, data.length);
            pos += data.length;
        }

        if (compressionType != CompressionType.NONE) {
            recordsData = compress(recordsData);
        }

        // Calculate batch length (everything after batchLength field)
        int batchLength = 4 + 1 + 4 + 2 + 4 + 8 + 8 + 8 + 2 + 4 + 4 + recordsData.length;

        ByteBuffer buffer = ByteBuffer.allocate(8 + 4 + batchLength);

        // Base offset
        buffer.putLong(baseOffset);

        // Batch length
        buffer.putInt(batchLength);

        // Partition leader epoch
        buffer.putInt(partitionLeaderEpoch);

        // Magic
        buffer.put(MAGIC_VALUE);

        // CRC placeholder (will calculate and update)
        int crcPosition = buffer.position();
        buffer.putInt(0);

        // Attributes
        short attributes = 0;
        attributes |= compressionType.id();
        if (timestampType == TimestampType.LOG_APPEND_TIME)
            attributes |= 0x08;
        if (isTransactional)
            attributes |= 0x10;
        if (isControlBatch)
            attributes |= 0x20;
        buffer.putShort(attributes);

        // Last offset delta
        buffer.putInt(records.size() - 1);

        // First timestamp
        buffer.putLong(firstTimestamp);

        // Max timestamp
        buffer.putLong(maxTimestamp);

        // Producer ID
        buffer.putLong(producerId);

        // Producer epoch
        buffer.putShort(producerEpoch);

        // Base sequence
        buffer.putInt(baseSequence);

        // Records count
        buffer.putInt(records.size());

        // Records data
        buffer.put(recordsData);

        // Calculate and update CRC
        byte[] data = buffer.array();
        CRC32 crc = new CRC32();
        crc.update(data, crcPosition + 4, data.length - crcPosition - 4);
        buffer.putInt(crcPosition, (int) crc.getValue());

        return buffer.array();
    }

    /**
     * Deserialize batch from bytes.
     */
    public static RecordBatch deserialize(ByteBuffer buffer) {
        long baseOffset = buffer.getLong();
        int batchLength = buffer.getInt();
        int partitionLeaderEpoch = buffer.getInt();
        byte magic = buffer.get();
        int crc = buffer.getInt();
        short attributes = buffer.getShort();
        int lastOffsetDelta = buffer.getInt();
        long firstTimestamp = buffer.getLong();
        long maxTimestamp = buffer.getLong();
        long producerId = buffer.getLong();
        short producerEpoch = buffer.getShort();
        int baseSequence = buffer.getInt();
        int recordCount = buffer.getInt();

        // Parse attributes
        CompressionType compression = CompressionType.fromId(attributes & 0x07);
        TimestampType timestampType = (attributes & 0x08) != 0 ? TimestampType.LOG_APPEND_TIME
                : TimestampType.CREATE_TIME;
        boolean isTransactional = (attributes & 0x10) != 0;
        boolean isControlBatch = (attributes & 0x20) != 0;

        // Read records data
        int recordsSize = batchLength - (4 + 1 + 4 + 2 + 4 + 8 + 8 + 8 + 2 + 4 + 4);
        byte[] recordsData = new byte[recordsSize];
        buffer.get(recordsData);

        // Decompress if needed
        if (compression != CompressionType.NONE) {
            recordsData = decompress(recordsData, compression);
        }

        // Parse records
        List<Record> records = new ArrayList<>();
        ByteBuffer recordBuffer = ByteBuffer.wrap(recordsData);
        for (int i = 0; i < recordCount; i++) {
            records.add(Record.deserialize(recordBuffer, baseOffset, firstTimestamp));
        }

        return RecordBatch.builder()
                .baseOffset(baseOffset)
                .partitionLeaderEpoch(partitionLeaderEpoch)
                .firstTimestamp(firstTimestamp)
                .maxTimestamp(maxTimestamp)
                .producerId(producerId)
                .producerEpoch(producerEpoch)
                .baseSequence(baseSequence)
                .compressionType(compression)
                .timestampType(timestampType)
                .isTransactional(isTransactional)
                .isControlBatch(isControlBatch)
                .records(records)
                .build();
    }

    /**
     * Add a record to this batch.
     */
    public void addRecord(Record record) {
        if (records.isEmpty()) {
            baseOffset = record.getOffset();
            firstTimestamp = record.getTimestamp();
        }
        maxTimestamp = Math.max(maxTimestamp, record.getTimestamp());
        records.add(record);
    }

    /**
     * Get the last offset in this batch.
     */
    public long lastOffset() {
        return baseOffset + records.size() - 1;
    }

    /**
     * Get the next offset after this batch.
     */
    public long nextOffset() {
        return baseOffset + records.size();
    }

    /**
     * Get total size in bytes.
     */
    public int sizeInBytes() {
        return serialize().length;
    }

    /**
     * Check if batch is full based on size limit.
     */
    public boolean isFull(int maxBatchSize) {
        return sizeInBytes() >= maxBatchSize;
    }

    // Compression helpers
    private byte[] compress(byte[] data) {
        try {
            switch (compressionType) {
                case LZ4:
                    return compressLZ4(data);
                case SNAPPY:
                    return compressSnappy(data);
                case GZIP:
                    return compressGzip(data);
                case ZSTD:
                    return compressZstd(data);
                default:
                    return data;
            }
        } catch (Exception e) {
            log.warn("Compression failed, using uncompressed: {}", e.getMessage());
            return data;
        }
    }

    private static byte[] decompress(byte[] data, CompressionType type) {
        try {
            switch (type) {
                case LZ4:
                    return decompressLZ4(data);
                case SNAPPY:
                    return decompressSnappy(data);
                case GZIP:
                    return decompressGzip(data);
                case ZSTD:
                    return decompressZstd(data);
                default:
                    return data;
            }
        } catch (Exception e) {
            log.warn("Decompression failed: {}", e.getMessage());
            return data;
        }
    }

    // LZ4 compression
    private byte[] compressLZ4(byte[] data) {
        try {
            net.jpountz.lz4.LZ4Factory factory = net.jpountz.lz4.LZ4Factory.fastestInstance();
            net.jpountz.lz4.LZ4Compressor compressor = factory.fastCompressor();
            int maxLen = compressor.maxCompressedLength(data.length);
            byte[] compressed = new byte[maxLen + 4]; // 4 bytes for original length
            ByteBuffer.wrap(compressed).putInt(data.length);
            int compressedLen = compressor.compress(data, 0, data.length, compressed, 4, maxLen);
            byte[] result = new byte[compressedLen + 4];
            System.arraycopy(compressed, 0, result, 0, compressedLen + 4);
            return result;
        } catch (Exception e) {
            return data;
        }
    }

    private static byte[] decompressLZ4(byte[] data) {
        try {
            net.jpountz.lz4.LZ4Factory factory = net.jpountz.lz4.LZ4Factory.fastestInstance();
            net.jpountz.lz4.LZ4FastDecompressor decompressor = factory.fastDecompressor();
            int originalLen = ByteBuffer.wrap(data).getInt();
            byte[] decompressed = new byte[originalLen];
            decompressor.decompress(data, 4, decompressed, 0, originalLen);
            return decompressed;
        } catch (Exception e) {
            return data;
        }
    }

    // Snappy compression
    private byte[] compressSnappy(byte[] data) {
        try {
            return org.xerial.snappy.Snappy.compress(data);
        } catch (Exception e) {
            return data;
        }
    }

    private static byte[] decompressSnappy(byte[] data) {
        try {
            return org.xerial.snappy.Snappy.uncompress(data);
        } catch (Exception e) {
            return data;
        }
    }

    // GZIP compression
    private byte[] compressGzip(byte[] data) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos);
            gzip.write(data);
            gzip.close();
            return baos.toByteArray();
        } catch (Exception e) {
            return data;
        }
    }

    private static byte[] decompressGzip(byte[] data) {
        try {
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
            java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(bais);
            return gzip.readAllBytes();
        } catch (Exception e) {
            return data;
        }
    }

    // ZSTD compression
    private byte[] compressZstd(byte[] data) {
        try {
            return com.github.luben.zstd.Zstd.compress(data);
        } catch (Exception e) {
            return data;
        }
    }

    private static byte[] decompressZstd(byte[] data) {
        try {
            long size = com.github.luben.zstd.Zstd.decompressedSize(data);
            return com.github.luben.zstd.Zstd.decompress(data, (int) size);
        } catch (Exception e) {
            return data;
        }
    }
}
