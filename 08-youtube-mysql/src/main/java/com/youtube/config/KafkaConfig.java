package com.youtube.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Configuration
 * 
 * Topics for async event processing:
 * - view-events: View counting (high volume)
 * - video-uploads: Video processing pipeline
 * - comment-events: Comment moderation
 * - like-events: Like/dislike processing
 */
@Configuration
public class KafkaConfig {

    // High volume - many partitions for parallelism
    @Bean
    public NewTopic viewEventsTopic() {
        return TopicBuilder.name("view-events")
                .partitions(32)
                .replicas(1)
                .config("retention.ms", "86400000") // 1 day
                .config("compression.type", "lz4")
                .build();
    }

    @Bean
    public NewTopic videoUploadsTopic() {
        return TopicBuilder.name("video-uploads")
                .partitions(16)
                .replicas(1)
                .config("retention.ms", "604800000") // 7 days
                .build();
    }

    @Bean
    public NewTopic videoUpdatesTopic() {
        return TopicBuilder.name("video-updates")
                .partitions(16)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic commentEventsTopic() {
        return TopicBuilder.name("comment-events")
                .partitions(16)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic likeEventsTopic() {
        return TopicBuilder.name("like-events")
                .partitions(16)
                .replicas(1)
                .config("compression.type", "lz4")
                .build();
    }

    @Bean
    public NewTopic subscriptionEventsTopic() {
        return TopicBuilder.name("subscription-events")
                .partitions(8)
                .replicas(1)
                .build();
    }
}
