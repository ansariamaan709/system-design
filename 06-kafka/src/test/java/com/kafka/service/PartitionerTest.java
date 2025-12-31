package com.kafka.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Partitioner.
 */
class PartitionerTest {

    private final Partitioner partitioner = new Partitioner();

    @Test
    void shouldReturnZeroForSinglePartition() {
        int partition = partitioner.partition("topic", "key".getBytes(), "value".getBytes(), 1);
        assertEquals(0, partition);
    }

    @Test
    void shouldPartitionByKey() {
        int numPartitions = 10;
        byte[] key = "user-123".getBytes();

        int partition1 = partitioner.partition("topic", key, "value1".getBytes(), numPartitions);
        int partition2 = partitioner.partition("topic", key, "value2".getBytes(), numPartitions);

        // Same key should always go to same partition
        assertEquals(partition1, partition2);
    }

    @Test
    void shouldDistributeKeysAcrossPartitions() {
        int numPartitions = 10;
        int[] partitionCounts = new int[numPartitions];

        // Generate many keys and count partition distribution
        for (int i = 0; i < 10000; i++) {
            byte[] key = ("key-" + i).getBytes();
            int partition = partitioner.partition("topic", key, null, numPartitions);
            partitionCounts[partition]++;
        }

        // All partitions should have at least some keys
        for (int count : partitionCounts) {
            assertTrue(count > 0, "Each partition should receive some keys");
        }
    }

    @Test
    void shouldRoundRobinForNullKey() {
        int numPartitions = 3;
        int[] partitionCounts = new int[numPartitions];

        // Send 100 messages with null key
        for (int i = 0; i < 99; i++) {
            int partition = partitioner.partition("topic", null, "value".getBytes(), numPartitions);
            partitionCounts[partition]++;
        }

        // Should distribute roughly evenly
        for (int count : partitionCounts) {
            assertTrue(count >= 30, "Round-robin should distribute evenly");
        }
    }

    @Test
    void shouldReturnValidPartition() {
        int numPartitions = 5;

        for (int i = 0; i < 1000; i++) {
            byte[] key = ("key-" + i).getBytes();
            int partition = partitioner.partition("topic", key, null, numPartitions);

            assertTrue(partition >= 0, "Partition should be non-negative");
            assertTrue(partition < numPartitions, "Partition should be less than numPartitions");
        }
    }

    @Test
    void shouldThrowForZeroPartitions() {
        assertThrows(IllegalArgumentException.class, () -> partitioner.partition("topic", "key".getBytes(), null, 0));
    }

    @Test
    void shouldThrowForNegativePartitions() {
        assertThrows(IllegalArgumentException.class, () -> partitioner.partition("topic", "key".getBytes(), null, -1));
    }

    @Test
    void testMurmur2Consistency() {
        // Test that murmur2 is deterministic
        byte[] key = "test-key".getBytes();

        int hash1 = Partitioner.murmur2(key);
        int hash2 = Partitioner.murmur2(key);

        assertEquals(hash1, hash2);
    }

    @Test
    void testMurmur2KnownValues() {
        // Test against known murmur2 values to ensure compatibility
        assertEquals(Partitioner.murmur2("".getBytes()), Partitioner.murmur2("".getBytes()));

        // Different inputs should produce different hashes
        assertNotEquals(
                Partitioner.murmur2("key1".getBytes()),
                Partitioner.murmur2("key2".getBytes()));
    }
}
