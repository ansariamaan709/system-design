package com.kafka.controller;

import com.kafka.entity.Partition;
import com.kafka.entity.Topic;
import com.kafka.service.TopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin Controller - REST API for Kafka administrative operations.
 * 
 * Endpoints:
 * - Topic management (create, delete, describe, list)
 * - Partition management (add partitions, get info)
 * - Configuration management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TopicService topicService;

    // ==================== Topic Operations ====================

    /**
     * Create a new topic.
     */
    @PostMapping("/topics")
    public ResponseEntity<TopicResponse> createTopic(@RequestBody CreateTopicRequest request) {
        log.info("Creating topic: {}", request.name());

        Topic topic = topicService.createTopic(
                request.name(),
                request.numPartitions(),
                request.replicationFactor(),
                request.configs());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TopicResponse.from(topic));
    }

    /**
     * Delete a topic.
     */
    @DeleteMapping("/topics/{name}")
    public ResponseEntity<Void> deleteTopic(@PathVariable String name) {
        log.info("Deleting topic: {}", name);
        topicService.deleteTopic(name);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get topic details.
     */
    @GetMapping("/topics/{name}")
    public ResponseEntity<TopicMetadataResponse> describeTopic(@PathVariable String name) {
        TopicService.TopicMetadata metadata = topicService.getTopicMetadata(name);
        return ResponseEntity.ok(TopicMetadataResponse.from(metadata));
    }

    /**
     * List all topics.
     */
    @GetMapping("/topics")
    public ResponseEntity<List<TopicResponse>> listTopics(
            @RequestParam(defaultValue = "false") boolean includeInternal) {

        List<Topic> topics = includeInternal
                ? topicService.getAllTopicsIncludingInternal()
                : topicService.getAllTopics();

        List<TopicResponse> response = topics.stream()
                .map(TopicResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Update topic configuration.
     */
    @PatchMapping("/topics/{name}/config")
    public ResponseEntity<TopicResponse> updateTopicConfig(
            @PathVariable String name,
            @RequestBody Map<String, String> configs) {

        log.info("Updating config for topic {}: {}", name, configs);
        Topic topic = topicService.updateConfig(name, configs);
        return ResponseEntity.ok(TopicResponse.from(topic));
    }

    // ==================== Partition Operations ====================

    /**
     * Add partitions to a topic.
     */
    @PostMapping("/topics/{name}/partitions")
    public ResponseEntity<List<PartitionResponse>> addPartitions(
            @PathVariable String name,
            @RequestBody AddPartitionsRequest request) {

        log.info("Adding partitions to topic {}: new count {}", name, request.totalCount());

        List<Partition> partitions = topicService.increasePartitions(name, request.totalCount());
        List<PartitionResponse> response = partitions.stream()
                .map(PartitionResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get partitions for a topic.
     */
    @GetMapping("/topics/{name}/partitions")
    public ResponseEntity<List<PartitionResponse>> getPartitions(@PathVariable String name) {
        List<Partition> partitions = topicService.getPartitions(name);
        List<PartitionResponse> response = partitions.stream()
                .map(PartitionResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get specific partition details.
     */
    @GetMapping("/topics/{name}/partitions/{partition}")
    public ResponseEntity<PartitionResponse> getPartition(
            @PathVariable String name,
            @PathVariable int partition) {

        Partition p = topicService.getPartition(name, partition);
        return ResponseEntity.ok(PartitionResponse.from(p));
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(TopicService.TopicNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTopicNotFound(TopicService.TopicNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("TOPIC_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(TopicService.TopicAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleTopicAlreadyExists(TopicService.TopicAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("TOPIC_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(TopicService.PartitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePartitionNotFound(TopicService.PartitionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PARTITION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", ex.getMessage()));
    }

    // ==================== Request/Response DTOs ====================

    public record CreateTopicRequest(
            String name,
            int numPartitions,
            int replicationFactor,
            Map<String, String> configs) {
        public CreateTopicRequest {
            if (numPartitions <= 0)
                numPartitions = 1;
            if (replicationFactor <= 0)
                replicationFactor = 1;
        }
    }

    public record AddPartitionsRequest(int totalCount) {
    }

    public record TopicResponse(
            String name,
            int numPartitions,
            int replicationFactor,
            boolean internal,
            String status,
            Map<String, Object> configs) {
        public static TopicResponse from(Topic topic) {
            return new TopicResponse(
                    topic.getName(),
                    topic.getPartitionCount(),
                    topic.getReplicationFactor(),
                    topic.getIsInternal(),
                    topic.getStatus().name(),
                    Map.of(
                            "retention.ms", topic.getRetentionMs(),
                            "retention.bytes", topic.getRetentionBytes(),
                            "segment.bytes", topic.getSegmentBytes(),
                            "cleanup.policy", topic.getCleanupPolicy().name(),
                            "compression.type", topic.getCompressionType().name(),
                            "min.insync.replicas", topic.getMinInsyncReplicas()));
        }
    }

    public record TopicMetadataResponse(
            String name,
            boolean internal,
            List<PartitionMetadata> partitions) {
        public static TopicMetadataResponse from(TopicService.TopicMetadata metadata) {
            List<PartitionMetadata> partitions = metadata.partitions().stream()
                    .map(p -> new PartitionMetadata(
                            p.partition(),
                            p.leader(),
                            p.replicas(),
                            p.isr(),
                            p.logEndOffset(),
                            p.highWatermark()))
                    .toList();
            return new TopicMetadataResponse(metadata.name(), metadata.internal(), partitions);
        }

        public record PartitionMetadata(
                int partition,
                Integer leader,
                List<Integer> replicas,
                List<Integer> isr,
                long logEndOffset,
                long highWatermark) {
        }
    }

    public record PartitionResponse(
            int partition,
            Integer leader,
            List<Integer> replicas,
            List<Integer> isr,
            long logEndOffset,
            long highWatermark,
            String status) {
        public static PartitionResponse from(Partition p) {
            return new PartitionResponse(
                    p.getPartitionNumber(),
                    p.getLeaderBrokerId(),
                    p.getReplicaBrokerIds(),
                    p.getIsrBrokerIds(),
                    p.getLogEndOffset(),
                    p.getHighWatermark(),
                    p.getStatus().name());
        }
    }

    public record ErrorResponse(String code, String message) {
    }
}
