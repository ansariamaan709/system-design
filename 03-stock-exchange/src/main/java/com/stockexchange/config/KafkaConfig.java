package com.stockexchange.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for trade and order event streaming.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ==================== Topics ====================

    @Bean
    public NewTopic tradesTopic() {
        return TopicBuilder.name("trades")
                .partitions(10) // Partition by symbol for ordering
                .replicas(1) // Increase in production
                .config("retention.ms", "604800000") // 7 days retention
                .config("segment.bytes", "1073741824") // 1GB segments
                .build();
    }

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name("orders")
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "604800000")
                .build();
    }

    @Bean
    public NewTopic marketDataTopic() {
        return TopicBuilder.name("market-data")
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "86400000") // 1 day retention
                .build();
    }

    @Bean
    public NewTopic riskAlertsTopic() {
        return TopicBuilder.name("risk-alerts")
                .partitions(3)
                .replicas(1)
                .config("retention.ms", "2592000000") // 30 days retention
                .build();
    }

    // ==================== Producer Configuration ====================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Performance tuning for low latency
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // Leader ack only for speed
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1); // Minimal batching delay
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB batch
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB buffer
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"); // Fast compression
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);

        // Idempotency for exactly-once semantics
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
