package com.kafka.storage;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RecordBatch.
 */
class RecordBatchTest {

    @Test
    void shouldCreateEmptyBatch() {
        RecordBatch batch = RecordBatch.builder().build();

        assertTrue(batch.getRecords().isEmpty());
    }

    @Test
    void shouldCreateBatchWithRecords() {
        List<Record> records = new ArrayList<>();
        records.add(createRecord(0));
        records.add(createRecord(1));
        records.add(createRecord(2));

        RecordBatch batch = RecordBatch.builder()
                .records(records)
                .build();

        assertEquals(3, batch.getRecords().size());
    }

    @Test
    void shouldTrackBaseOffset() {
        List<Record> records = List.of(createRecord(0));
        RecordBatch batch = RecordBatch.builder()
                .baseOffset(100)
                .records(records)
                .build();

        assertEquals(100, batch.getBaseOffset());
    }

    @Test
    void shouldTrackTimestamps() {
        long ts1 = 1000L;
        long ts2 = 2000L;
        long ts3 = 3000L;

        List<Record> records = List.of(
                Record.builder().offset(0).timestamp(ts1).value("v1".getBytes()).build(),
                Record.builder().offset(1).timestamp(ts2).value("v2".getBytes()).build(),
                Record.builder().offset(2).timestamp(ts3).value("v3".getBytes()).build());

        RecordBatch batch = RecordBatch.builder()
                .firstTimestamp(ts1)
                .maxTimestamp(ts3)
                .records(records)
                .build();

        assertEquals(ts1, batch.getFirstTimestamp());
        assertEquals(ts3, batch.getMaxTimestamp());
    }

    @Test
    void shouldSerializeAndDeserialize() {
        List<Record> records = List.of(createRecord(0), createRecord(1));
        RecordBatch original = RecordBatch.builder()
                .baseOffset(50)
                .firstTimestamp(System.currentTimeMillis())
                .maxTimestamp(System.currentTimeMillis())
                .records(records)
                .build();

        // Serialize
        byte[] serialized = original.serialize();
        assertNotNull(serialized);

        // Deserialize
        RecordBatch deserialized = RecordBatch.deserialize(ByteBuffer.wrap(serialized));

        assertEquals(original.getBaseOffset(), deserialized.getBaseOffset());
        assertEquals(original.getRecords().size(), deserialized.getRecords().size());
    }

    @Test
    void shouldSerializeWithCompression() {
        List<Record> records = List.of(createRecord(0), createRecord(1));
        RecordBatch batch = RecordBatch.builder()
                .compressionType(RecordBatch.CompressionType.GZIP)
                .firstTimestamp(System.currentTimeMillis())
                .maxTimestamp(System.currentTimeMillis())
                .records(records)
                .build();

        // Serialize with compression
        byte[] serialized = batch.serialize();
        assertNotNull(serialized);

        // Deserialize
        RecordBatch deserialized = RecordBatch.deserialize(ByteBuffer.wrap(serialized));

        assertEquals(2, deserialized.getRecords().size());
    }

    @Test
    void shouldCalculateSizeInBytes() {
        RecordBatch emptyBatch = RecordBatch.builder().build();

        int emptySize = emptyBatch.sizeInBytes();
        assertTrue(emptySize > 0);

        List<Record> records = List.of(createRecord(0));
        RecordBatch batchWithRecord = RecordBatch.builder()
                .records(records)
                .firstTimestamp(System.currentTimeMillis())
                .maxTimestamp(System.currentTimeMillis())
                .build();

        int sizeWithRecord = batchWithRecord.sizeInBytes();
        assertTrue(sizeWithRecord > emptySize);
    }

    @Test
    void shouldValidateCrc() {
        List<Record> records = List.of(createRecord(0));
        RecordBatch batch = RecordBatch.builder()
                .records(records)
                .firstTimestamp(System.currentTimeMillis())
                .maxTimestamp(System.currentTimeMillis())
                .build();

        byte[] serialized = batch.serialize();

        // Should not throw
        assertDoesNotThrow(() -> RecordBatch.deserialize(ByteBuffer.wrap(serialized)));
    }

    @Test
    void shouldSupportLz4Compression() {
        List<Record> records = new ArrayList<>();
        // Add lots of repetitive data (good for compression)
        for (int i = 0; i < 100; i++) {
            records.add(Record.builder()
                    .offset(i)
                    .timestamp(System.currentTimeMillis())
                    .value("repetitive data value".getBytes())
                    .build());
        }

        RecordBatch batch = RecordBatch.builder()
                .compressionType(RecordBatch.CompressionType.LZ4)
                .firstTimestamp(System.currentTimeMillis())
                .maxTimestamp(System.currentTimeMillis())
                .records(records)
                .build();

        byte[] compressed = batch.serialize();
        RecordBatch decompressed = RecordBatch.deserialize(ByteBuffer.wrap(compressed));

        assertEquals(100, decompressed.getRecords().size());
    }

    @Test
    void shouldSupportSnappyCompression() {
        List<Record> records = List.of(createRecord(0));
        RecordBatch batch = RecordBatch.builder()
                .compressionType(RecordBatch.CompressionType.SNAPPY)
                .firstTimestamp(System.currentTimeMillis())
                .maxTimestamp(System.currentTimeMillis())
                .records(records)
                .build();

        byte[] compressed = batch.serialize();
        RecordBatch decompressed = RecordBatch.deserialize(ByteBuffer.wrap(compressed));

        assertEquals(1, decompressed.getRecords().size());
    }

    @Test
    void shouldIterateRecords() {
        List<Record> records = List.of(createRecord(0), createRecord(1), createRecord(2));
        RecordBatch batch = RecordBatch.builder()
                .records(records)
                .build();

        List<Record> batchRecords = batch.getRecords();

        assertEquals(3, batchRecords.size());
        for (int i = 0; i < batchRecords.size(); i++) {
            assertEquals(i, batchRecords.get(i).getOffset());
        }
    }

    @Test
    void shouldSetProducerState() {
        RecordBatch batch = RecordBatch.builder()
                .producerId(12345L)
                .producerEpoch((short) 1)
                .baseSequence(100)
                .build();

        assertEquals(12345L, batch.getProducerId());
        assertEquals((short) 1, batch.getProducerEpoch());
        assertEquals(100, batch.getBaseSequence());
    }

    private Record createRecord(long offset) {
        return Record.builder()
                .offset(offset)
                .timestamp(System.currentTimeMillis())
                .key(("key-" + offset).getBytes())
                .value(("value-" + offset).getBytes())
                .build();
    }
}
