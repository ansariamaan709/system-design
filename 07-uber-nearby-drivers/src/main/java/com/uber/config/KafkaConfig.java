package com.uber.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for location event streaming.
 * 
 * Topics:
 * - location.updates: Driver location updates (high volume)
 * - ride.requests: Ride request events
 * - ride.matches: Driver-rider match events
 * - driver.status: Driver status changes
 */
@Configuration
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;
    
    // Topic names
    public static final String TOPIC_LOCATION_UPDATES = "location.updates";
    public static final String TOPIC_RIDE_REQUESTS = "ride.requests";
    public static final String TOPIC_RIDE_MATCHES = "ride.matches";
    public static final String TOPIC_DRIVER_STATUS = "driver.status";
    
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }
    
    // Location updates topic - high volume, partitioned by city/geohash
    @Bean
    public NewTopic locationUpdatesTopic() {
        return TopicBuilder.name(TOPIC_LOCATION_UPDATES)
                .partitions(32)  // High partition count for parallelism
                .replicas(1)     // Set to 3 in production
                .config("retention.ms", "86400000")  // 24 hour retention
                .config("segment.bytes", "1073741824")  // 1GB segments
                .build();
    }
    
    // Ride requests topic
    @Bean
    public NewTopic rideRequestsTopic() {
        return TopicBuilder.name(TOPIC_RIDE_REQUESTS)
                .partitions(16)
                .replicas(1)
                .config("retention.ms", "604800000")  // 7 day retention
                .build();
    }
    
    // Ride matches topic
    @Bean
    public NewTopic rideMatchesTopic() {
        return TopicBuilder.name(TOPIC_RIDE_MATCHES)
                .partitions(16)
                .replicas(1)
                .config("retention.ms", "604800000")
                .build();
    }
    
    // Driver status topic
    @Bean
    public NewTopic driverStatusTopic() {
        return TopicBuilder.name(TOPIC_DRIVER_STATUS)
                .partitions(8)
                .replicas(1)
                .config("retention.ms", "86400000")
                .build();
    }
    
    // Producer configuration
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Throughput optimizations
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");  // Leader ack only for speed
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);  // 32KB batches
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);  // Wait up to 5ms for batching
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864);  // 64MB buffer
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");  // Fast compression
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);  // Don't block too long
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    // Consumer configuration
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "uber-nearby-drivers");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.uber.dto,com.uber.entity");
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(4);  // 4 consumer threads
        factory.setBatchListener(true);  // Process in batches
        return factory;
    }
}
