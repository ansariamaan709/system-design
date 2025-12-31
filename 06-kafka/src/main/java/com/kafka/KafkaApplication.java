package com.kafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kafka Clone - Distributed Event Streaming Platform
 * 
 * This is a production-grade implementation of a message broker inspired by
 * Apache Kafka.
 * 
 * Core Features:
 * - Distributed pub/sub messaging with topic partitioning
 * - Persistent, ordered, append-only logs
 * - Consumer groups with automatic rebalancing
 * - At-least-once and exactly-once delivery semantics
 * - Horizontal scalability through partitioning
 * 
 * Architecture:
 * - Brokers: Store and serve messages
 * - Topics: Named streams partitioned for parallelism
 * - Producers: Publish messages to topics
 * - Consumers: Subscribe to topics via consumer groups
 * - Controller: Manages cluster metadata and leader election
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class KafkaApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaApplication.class, args);
    }
}
