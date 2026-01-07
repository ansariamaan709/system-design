package com.youtube.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Snowflake ID Generator
 */
class SnowflakeIdGeneratorTest {

    @Test
    void shouldGenerateUniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);

        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            long id = generator.nextId();
            assertTrue(ids.add(id), "Generated duplicate ID: " + id);
        }

        assertEquals(10000, ids.size());
    }

    @Test
    void shouldGenerateMonotonicallyIncreasingIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);

        long previousId = 0;
        for (int i = 0; i < 1000; i++) {
            long id = generator.nextId();
            assertTrue(id > previousId, "ID should be monotonically increasing");
            previousId = id;
        }
    }

    @Test
    void shouldParseIdComponents() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(5, 10);

        long id = generator.nextId();
        SnowflakeIdGenerator.IdComponents components = generator.parse(id);

        assertEquals(5, components.datacenterId());
        assertEquals(10, components.workerId());
        assertNotNull(components.timestamp());
    }

    @Test
    void shouldHandleConcurrentGeneration() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        Set<Long> ids = java.util.Collections.synchronizedSet(new HashSet<>());

        int threadCount = 10;
        int idsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < idsPerThread; i++) {
                        long id = generator.nextId();
                        ids.add(id);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * idsPerThread, ids.size(),
                "All generated IDs should be unique");
    }

    @Test
    void shouldRejectInvalidDatacenterId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32, 1));
    }

    @Test
    void shouldRejectInvalidWorkerId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1, 32));
    }

    @Test
    void shouldGeneratePositiveIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);

        for (int i = 0; i < 1000; i++) {
            long id = generator.nextId();
            assertTrue(id > 0, "ID should be positive");
        }
    }
}
