package com.kafka.storage;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Record serialization and deserialization.
 */
class RecordTest {

    @Test
    void shouldCreateRecordWithAllFields() {
        byte[] key = "test-key".getBytes();
        byte[] value = "test-value".getBytes();
        List<Record.Header> headers = List.of(
                new Record.Header("header1", "value1".getBytes()));

        Record record = Record.builder()
                .offset(42)
                .timestamp(1234567890L)
                .key(key)
                .value(value)
                .headers(headers)
                .build();

        assertEquals(42, record.getOffset());
        assertEquals(1234567890L, record.getTimestamp());
        assertArrayEquals(key, record.getKey());
        assertArrayEquals(value, record.getValue());
        assertEquals(1, record.getHeaders().size());
    }

    @Test
    void shouldSerializeAndDeserialize() {
        long timestamp = System.currentTimeMillis();
        Record original = Record.builder()
                .offset(100)
                .timestamp(timestamp)
                .key("key".getBytes())
                .value("value".getBytes())
                .build();

        // Serialize
        byte[] serialized = original.serialize();
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        // Deserialize
        Record deserialized = Record.deserialize(ByteBuffer.wrap(serialized), 100, timestamp);

        assertEquals(original.getOffset(), deserialized.getOffset());
        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
        assertArrayEquals(original.getKey(), deserialized.getKey());
        assertArrayEquals(original.getValue(), deserialized.getValue());
    }

    @Test
    void shouldHandleNullKey() {
        long timestamp = System.currentTimeMillis();
        Record record = Record.builder()
                .offset(0)
                .timestamp(timestamp)
                .key(null)
                .value("value".getBytes())
                .build();

        byte[] serialized = record.serialize();
        Record deserialized = Record.deserialize(ByteBuffer.wrap(serialized), 0, timestamp);

        assertNull(deserialized.getKey());
        assertArrayEquals("value".getBytes(), deserialized.getValue());
    }

    @Test
    void shouldHandleNullValue() {
        long timestamp = System.currentTimeMillis();
        Record record = Record.builder()
                .offset(0)
                .timestamp(timestamp)
                .key("key".getBytes())
                .value(null)
                .build();

        byte[] serialized = record.serialize();
        Record deserialized = Record.deserialize(ByteBuffer.wrap(serialized), 0, timestamp);

        assertArrayEquals("key".getBytes(), deserialized.getKey());
        assertNull(deserialized.getValue());
    }

    @Test
    void shouldSerializeHeaders() {
        List<Record.Header> headers = List.of(
                new Record.Header("h1", "v1".getBytes()),
                new Record.Header("h2", "v2".getBytes()));

        long timestamp = System.currentTimeMillis();
        Record record = Record.builder()
                .offset(0)
                .timestamp(timestamp)
                .key("k".getBytes())
                .value("v".getBytes())
                .headers(headers)
                .build();

        byte[] serialized = record.serialize();
        Record deserialized = Record.deserialize(ByteBuffer.wrap(serialized), 0, timestamp);

        assertEquals(2, deserialized.getHeaders().size());
        assertEquals("h1", deserialized.getHeaders().get(0).getKey());
        assertArrayEquals("v1".getBytes(), deserialized.getHeaders().get(0).getValue());
    }

    @Test
    void shouldCalculateSizeInBytes() {
        long timestamp = System.currentTimeMillis();
        Record record = Record.builder()
                .offset(0)
                .timestamp(timestamp)
                .key("key".getBytes())
                .value("value".getBytes())
                .build();

        int size = record.sizeInBytes();

        assertTrue(size > 0);
        assertEquals(record.serialize().length, size);
    }

    @Test
    void shouldCalculateCrc() {
        long timestamp = System.currentTimeMillis();
        Record record = Record.builder()
                .offset(0)
                .timestamp(timestamp)
                .key("key".getBytes())
                .value("value".getBytes())
                .build();

        long crc = record.calculateCrc();

        assertTrue(crc != 0);
    }

    @Test
    void shouldHandleLargeValue() {
        byte[] largeValue = new byte[100_000];
        for (int i = 0; i < largeValue.length; i++) {
            largeValue[i] = (byte) (i % 256);
        }

        long timestamp = System.currentTimeMillis();
        Record record = Record.builder()
                .offset(0)
                .timestamp(timestamp)
                .key("key".getBytes())
                .value(largeValue)
                .build();

        byte[] serialized = record.serialize();
        Record deserialized = Record.deserialize(ByteBuffer.wrap(serialized), 0, timestamp);

        assertArrayEquals(largeValue, deserialized.getValue());
    }
}
