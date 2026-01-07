package com.youtube.id;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Snowflake ID Generator - YouTube-style distributed ID generation
 * 
 * Structure (64 bits):
 * - 1 bit: unused (sign bit)
 * - 41 bits: timestamp (milliseconds since custom epoch, ~69 years)
 * - 10 bits: machine ID (datacenter + worker, 1024 machines)
 * - 12 bits: sequence number (4096 IDs per millisecond per machine)
 * 
 * Capacity: 4,096 IDs/ms/worker × 1,024 workers = 4.1M IDs/second
 */
@Component
public class SnowflakeIdGenerator {

    // Custom epoch: 2024-01-01 00:00:00 UTC
    private static final long CUSTOM_EPOCH = 1704067200000L;

    // Bit lengths
    private static final int DATACENTER_ID_BITS = 5;
    private static final int WORKER_ID_BITS = 5;
    private static final int SEQUENCE_BITS = 12;

    // Max values
    private static final long MAX_DATACENTER_ID = (1L << DATACENTER_ID_BITS) - 1; // 31
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1; // 31
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1; // 4095

    // Bit shifts
    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final int DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long datacenterId;
    private final long workerId;

    private final AtomicLong sequence = new AtomicLong(0);
    private volatile long lastTimestamp = -1L;

    public SnowflakeIdGenerator(
            @Value("${youtube.id-generator.datacenter-id:1}") long datacenterId,
            @Value("${youtube.id-generator.worker-id:1}") long workerId) {

        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(
                    String.format("Datacenter ID must be between 0 and %d", MAX_DATACENTER_ID));
        }
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    String.format("Worker ID must be between 0 and %d", MAX_WORKER_ID));
        }

        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    /**
     * Generate next unique ID
     * Thread-safe using synchronization
     */
    public synchronized long nextId() {
        long currentTimestamp = currentTimeMillis();

        // Handle clock moving backwards
        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException(
                    String.format("Clock moved backwards. Refusing to generate ID for %d milliseconds",
                            lastTimestamp - currentTimestamp));
        }

        if (currentTimestamp == lastTimestamp) {
            // Same millisecond - increment sequence
            long seq = sequence.incrementAndGet() & MAX_SEQUENCE;
            if (seq == 0) {
                // Sequence exhausted - wait for next millisecond
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // New millisecond - reset sequence
            sequence.set(0);
        }

        lastTimestamp = currentTimestamp;

        // Build ID
        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence.get();
    }

    /**
     * Parse ID to extract components
     */
    public IdComponents parse(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH;
        long dcId = (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
        long wkId = (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        long seq = id & MAX_SEQUENCE;

        return new IdComponents(
                Instant.ofEpochMilli(timestamp),
                dcId,
                wkId,
                seq);
    }

    private long waitNextMillis(long lastTs) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTs) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * ID components for debugging/analysis
     */
    public record IdComponents(
            Instant timestamp,
            long datacenterId,
            long workerId,
            long sequence) {
    }
}
