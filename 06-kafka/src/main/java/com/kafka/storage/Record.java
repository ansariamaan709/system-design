package com.kafka.storage;

import lombok.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Record represents a single message in Kafka.
 * 
 * This follows Kafka's v2 record format which is more compact
 * than v0/v1 and supports headers.
 * 
 * Record layout (variable length):
 * - Length (varint)
 * - Attributes (1 byte)
 * - Timestamp Delta (varint)
 * - Offset Delta (varint)
 * - Key Length (varint)
 * - Key (bytes)
 * - Value Length (varint)
 * - Value (bytes)
 * - Header Count (varint)
 * - Headers (array)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Record {

    private long offset;
    private long timestamp;
    private byte[] key;
    private byte[] value;

    @Builder.Default
    private List<Header> headers = new ArrayList<>();

    // For idempotence
    private Long producerId;
    private Short producerEpoch;
    private Integer sequence;

    // Attributes
    private boolean isTransactional;
    private boolean isControlRecord;

    /**
     * Header represents a key-value pair attached to a record.
     */
    @Getter
    @AllArgsConstructor
    public static class Header {
        private final String key;
        private final byte[] value;
    }

    /**
     * Serialize record to bytes.
     */
    public byte[] serialize() {
        // Calculate size
        int keyLen = key != null ? key.length : 0;
        int valueLen = value != null ? value.length : 0;
        int headersSize = calculateHeadersSize();

        int size = 1 // attributes
                + varIntSize(0) // timestamp delta (0 for single record)
                + varIntSize(0) // offset delta
                + varIntSize(keyLen) + keyLen
                + varIntSize(valueLen) + valueLen
                + varIntSize(headers.size()) + headersSize;

        ByteBuffer buffer = ByteBuffer.allocate(varIntSize(size) + size);

        // Length
        writeVarInt(buffer, size);

        // Attributes
        byte attributes = 0;
        if (isTransactional)
            attributes |= 0x10;
        if (isControlRecord)
            attributes |= 0x20;
        buffer.put(attributes);

        // Timestamp delta
        writeVarInt(buffer, 0);

        // Offset delta
        writeVarInt(buffer, 0);

        // Key
        writeVarInt(buffer, keyLen > 0 ? keyLen : -1);
        if (key != null)
            buffer.put(key);

        // Value
        writeVarInt(buffer, valueLen > 0 ? valueLen : -1);
        if (value != null)
            buffer.put(value);

        // Headers
        writeVarInt(buffer, headers.size());
        for (Header header : headers) {
            byte[] headerKey = header.getKey().getBytes();
            writeVarInt(buffer, headerKey.length);
            buffer.put(headerKey);
            writeVarInt(buffer, header.getValue() != null ? header.getValue().length : -1);
            if (header.getValue() != null)
                buffer.put(header.getValue());
        }

        return buffer.array();
    }

    /**
     * Deserialize record from bytes.
     */
    public static Record deserialize(ByteBuffer buffer, long baseOffset, long baseTimestamp) {
        int length = readVarInt(buffer);
        byte attributes = buffer.get();
        long timestampDelta = readVarInt(buffer);
        int offsetDelta = readVarInt(buffer);

        int keyLen = readVarInt(buffer);
        byte[] key = null;
        if (keyLen >= 0) {
            key = new byte[keyLen];
            buffer.get(key);
        }

        int valueLen = readVarInt(buffer);
        byte[] value = null;
        if (valueLen >= 0) {
            value = new byte[valueLen];
            buffer.get(value);
        }

        int headerCount = readVarInt(buffer);
        List<Header> headers = new ArrayList<>();
        for (int i = 0; i < headerCount; i++) {
            int headerKeyLen = readVarInt(buffer);
            byte[] headerKeyBytes = new byte[headerKeyLen];
            buffer.get(headerKeyBytes);
            String headerKey = new String(headerKeyBytes);

            int headerValueLen = readVarInt(buffer);
            byte[] headerValue = null;
            if (headerValueLen >= 0) {
                headerValue = new byte[headerValueLen];
                buffer.get(headerValue);
            }
            headers.add(new Header(headerKey, headerValue));
        }

        return Record.builder()
                .offset(baseOffset + offsetDelta)
                .timestamp(baseTimestamp + timestampDelta)
                .key(key)
                .value(value)
                .headers(headers)
                .isTransactional((attributes & 0x10) != 0)
                .isControlRecord((attributes & 0x20) != 0)
                .build();
    }

    private int calculateHeadersSize() {
        int size = 0;
        for (Header header : headers) {
            byte[] keyBytes = header.getKey().getBytes();
            size += varIntSize(keyBytes.length) + keyBytes.length;
            size += varIntSize(header.getValue() != null ? header.getValue().length : -1);
            if (header.getValue() != null)
                size += header.getValue().length;
        }
        return size;
    }

    // Variable-length integer encoding (zigzag + varint)
    private static void writeVarInt(ByteBuffer buffer, int value) {
        int v = (value << 1) ^ (value >> 31); // zigzag encoding
        while ((v & ~0x7F) != 0) {
            buffer.put((byte) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        buffer.put((byte) v);
    }

    private static int readVarInt(ByteBuffer buffer) {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = buffer.get();
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return (value >>> 1) ^ -(value & 1); // zigzag decoding
    }

    private static int varIntSize(int value) {
        int v = (value << 1) ^ (value >> 31);
        int size = 1;
        while ((v & ~0x7F) != 0) {
            size++;
            v >>>= 7;
        }
        return size;
    }

    /**
     * Calculate CRC32 checksum.
     */
    public long calculateCrc() {
        CRC32 crc = new CRC32();
        if (key != null)
            crc.update(key);
        if (value != null)
            crc.update(value);
        return crc.getValue();
    }

    /**
     * Get size of this record in bytes.
     */
    public int sizeInBytes() {
        return serialize().length;
    }

    /**
     * Get key as string.
     */
    public String keyAsString() {
        return key != null ? new String(key) : null;
    }

    /**
     * Get value as string.
     */
    public String valueAsString() {
        return value != null ? new String(value) : null;
    }
}
