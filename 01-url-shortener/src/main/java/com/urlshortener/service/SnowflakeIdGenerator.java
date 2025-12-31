package com.urlshortener.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Snowflake-like ID generator for distributed unique IDs.
 * 
 * Structure (64 bits):
 * - 1 bit: sign (always 0)
 * - 41 bits: timestamp (milliseconds since custom epoch)
 * - 5 bits: datacenter ID
 * - 5 bits: machine ID
 * - 12 bits: sequence number
 */
@Component
public class SnowflakeIdGenerator {

    // Custom epoch: 2024-01-01 00:00:00 UTC
    private static final long EPOCH = 1704067200000L;

    private static final long DATACENTER_ID_BITS = 5L;
    private static final long MACHINE_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATACENTER_ID_BITS;

    private final long datacenterId;
    private final long machineId;
    private final AtomicLong sequence = new AtomicLong(0L);
    private volatile long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        // Default to random datacenter and machine IDs
        SecureRandom random = new SecureRandom();
        this.datacenterId = random.nextInt((int) MAX_DATACENTER_ID + 1);
        this.machineId = random.nextInt((int) MAX_MACHINE_ID + 1);
    }

    public SnowflakeIdGenerator(long datacenterId, long machineId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("Datacenter ID must be between 0 and " + MAX_DATACENTER_ID);
        }
        if (machineId > MAX_MACHINE_ID || machineId < 0) {
            throw new IllegalArgumentException("Machine ID must be between 0 and " + MAX_MACHINE_ID);
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID");
        }

        if (currentTimestamp == lastTimestamp) {
            long seq = sequence.incrementAndGet() & MAX_SEQUENCE;
            if (seq == 0) {
                // Sequence exhausted, wait for next millisecond
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence.set(0L);
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence.get();
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * Extract timestamp from a generated ID.
     */
    public long extractTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }

    /**
     * Extract datacenter ID from a generated ID.
     */
    public long extractDatacenterId(long id) {
        return (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    /**
     * Extract machine ID from a generated ID.
     */
    public long extractMachineId(long id) {
        return (id >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
    }
}
