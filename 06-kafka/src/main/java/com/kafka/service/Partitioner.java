package com.kafka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Partitioner - Determines which partition a record should be sent to.
 * 
 * Kafka's default partitioning strategy:
 * 1. If partition is specified explicitly, use it
 * 2. If key is present, hash the key (murmur2) and mod by partition count
 * 3. If no key, use round-robin (sticky partitioning in newer versions)
 * 
 * This implementation uses murmur2 hash for consistency with Apache Kafka.
 */
@Slf4j
@Component
public class Partitioner {

    // Counter for round-robin when no key is provided
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    /**
     * Compute the partition for the given record.
     */
    public int partition(String topic, byte[] key, byte[] value, int numPartitions) {
        if (numPartitions <= 0) {
            throw new IllegalArgumentException("Number of partitions must be positive");
        }

        if (key == null) {
            // Round-robin for keyless messages (simplified - Kafka uses sticky
            // partitioning)
            return roundRobin(numPartitions);
        }

        // Use murmur2 hash of key
        return toPositive(murmur2(key)) % numPartitions;
    }

    /**
     * Round-robin partition assignment for keyless messages.
     */
    private int roundRobin(int numPartitions) {
        int partition = roundRobinCounter.getAndIncrement();
        if (partition < 0) {
            // Handle overflow
            roundRobinCounter.set(0);
            partition = 0;
        }
        return partition % numPartitions;
    }

    /**
     * Convert a number to a positive value.
     */
    private static int toPositive(int number) {
        return number & 0x7fffffff;
    }

    /**
     * Murmur2 hash implementation.
     * This is the same algorithm used by Apache Kafka for partition assignment.
     * 
     * MurmurHash2 was chosen for:
     * - Good distribution properties
     * - Fast computation
     * - Deterministic across languages
     */
    public static int murmur2(byte[] data) {
        int length = data.length;
        int seed = 0x9747b28c; // Same seed as Kafka

        // 'm' and 'r' are mixing constants generated offline.
        // They're not really 'magic', they just happen to work well.
        int m = 0x5bd1e995;
        int r = 24;

        // Initialize the hash to a random value
        int h = seed ^ length;

        int length4 = length / 4;

        for (int i = 0; i < length4; i++) {
            int i4 = i * 4;
            int k = (data[i4 + 0] & 0xff)
                    + ((data[i4 + 1] & 0xff) << 8)
                    + ((data[i4 + 2] & 0xff) << 16)
                    + ((data[i4 + 3] & 0xff) << 24);

            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        // Handle the last few bytes of the input array
        int remaining = length % 4;
        int offset = length4 * 4;

        switch (remaining) {
            case 3:
                h ^= (data[offset + 2] & 0xff) << 16;
                // fall through
            case 2:
                h ^= (data[offset + 1] & 0xff) << 8;
                // fall through
            case 1:
                h ^= (data[offset] & 0xff);
                h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }

    /**
     * Custom partitioner interface for users who want different behavior.
     */
    public interface CustomPartitioner {
        int partition(String topic, byte[] key, byte[] value, int numPartitions);
    }

    /**
     * Uniform sticky partitioner - sends batches to the same partition.
     * This reduces latency by allowing larger batches.
     */
    public static class StickyPartitioner implements CustomPartitioner {
        private final ThreadLocal<Integer> stickyPartition = new ThreadLocal<>();
        private final ThreadLocal<Integer> recordCount = ThreadLocal.withInitial(() -> 0);
        private final int batchSize;

        public StickyPartitioner(int batchSize) {
            this.batchSize = batchSize;
        }

        @Override
        public int partition(String topic, byte[] key, byte[] value, int numPartitions) {
            if (key != null) {
                return toPositive(murmur2(key)) % numPartitions;
            }

            Integer currentPartition = stickyPartition.get();
            int count = recordCount.get();

            if (currentPartition == null || count >= batchSize) {
                // Change partition
                currentPartition = (int) (Math.random() * numPartitions);
                stickyPartition.set(currentPartition);
                recordCount.set(1);
            } else {
                recordCount.set(count + 1);
            }

            return currentPartition;
        }
    }

    /**
     * Range partitioner - for ordered data based on key ranges.
     */
    public static class RangePartitioner implements CustomPartitioner {
        private final List<byte[]> boundaries;

        public RangePartitioner(List<byte[]> boundaries) {
            this.boundaries = boundaries;
        }

        @Override
        public int partition(String topic, byte[] key, byte[] value, int numPartitions) {
            if (key == null) {
                return 0;
            }

            // Binary search for the partition
            for (int i = 0; i < boundaries.size(); i++) {
                if (compare(key, boundaries.get(i)) < 0) {
                    return i;
                }
            }
            return boundaries.size();
        }

        private int compare(byte[] a, byte[] b) {
            int minLen = Math.min(a.length, b.length);
            for (int i = 0; i < minLen; i++) {
                int cmp = (a[i] & 0xff) - (b[i] & 0xff);
                if (cmp != 0)
                    return cmp;
            }
            return a.length - b.length;
        }
    }
}
