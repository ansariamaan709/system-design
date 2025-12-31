package com.urlshortener.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeIdGeneratorTest {

    private SnowflakeIdGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SnowflakeIdGenerator(1L, 1L);
    }

    @Test
    @DisplayName("Should generate positive IDs")
    void shouldGeneratePositiveIds() {
        // When
        long id = generator.nextId();

        // Then
        assertThat(id).isPositive();
    }

    @RepeatedTest(100)
    @DisplayName("Should generate unique IDs across multiple calls")
    void shouldGenerateUniqueIds() {
        // Given
        Set<Long> ids = new HashSet<>();
        int count = 1000;

        // When
        for (int i = 0; i < count; i++) {
            ids.add(generator.nextId());
        }

        // Then
        assertThat(ids).hasSize(count);
    }

    @Test
    @DisplayName("Should generate unique IDs in concurrent environment")
    void shouldGenerateUniqueIdsInConcurrentEnvironment() throws InterruptedException {
        // Given
        int threadCount = 10;
        int idsPerThread = 1000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        ids.add(generator.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then
        assertThat(ids).hasSize(threadCount * idsPerThread);
    }

    @Test
    @DisplayName("Should generate monotonically increasing IDs")
    void shouldGenerateMonotonicallyIncreasingIds() {
        // Given
        long previousId = 0;

        // When/Then
        for (int i = 0; i < 1000; i++) {
            long currentId = generator.nextId();
            assertThat(currentId).isGreaterThan(previousId);
            previousId = currentId;
        }
    }

    @Test
    @DisplayName("Different generators should produce different IDs")
    void differentGeneratorsShouldProduceDifferentIds() {
        // Given
        SnowflakeIdGenerator generator1 = new SnowflakeIdGenerator(1L, 1L);
        SnowflakeIdGenerator generator2 = new SnowflakeIdGenerator(1L, 2L);

        // When
        long id1 = generator1.nextId();
        long id2 = generator2.nextId();

        // Then
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("Should extract timestamp from ID")
    void shouldExtractTimestampFromId() {
        // Given
        long beforeGeneration = System.currentTimeMillis();
        long id = generator.nextId();
        long afterGeneration = System.currentTimeMillis();

        // When
        long extractedTimestamp = generator.extractTimestamp(id);

        // Then
        assertThat(extractedTimestamp).isBetween(beforeGeneration, afterGeneration);
    }
}
