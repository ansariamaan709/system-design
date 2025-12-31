package com.kafka.service;

import com.kafka.entity.Partition;
import com.kafka.entity.Topic;
import com.kafka.repository.PartitionRepository;
import com.kafka.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for managing topics and partitions.
 * Handles topic creation, deletion, configuration changes, and partition
 * expansion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topicRepository;
    private final PartitionRepository partitionRepository;
    private final ReplicaAssignmentService replicaAssignmentService;

    /**
     * Create a new topic with specified configuration.
     */
    @Transactional
    public Topic createTopic(String name, int numPartitions, int replicationFactor,
            Map<String, String> configs) {
        log.info("Creating topic: {} with {} partitions, replication factor {}",
                name, numPartitions, replicationFactor);

        // Validate topic name
        validateTopicName(name);

        // Check if topic already exists
        if (topicRepository.existsByName(name)) {
            throw new TopicAlreadyExistsException("Topic already exists: " + name);
        }

        // Create topic entity
        Topic topic = new Topic();
        topic.setName(name);
        topic.setPartitionCount(numPartitions);
        topic.setReplicationFactor(replicationFactor);
        topic.setStatus(Topic.TopicStatus.ACTIVE);

        // Apply configurations
        applyTopicConfigs(topic, configs);

        topic = topicRepository.save(topic);

        // Create partitions
        List<Partition> partitions = createPartitions(topic, numPartitions, replicationFactor);

        // Update topic status
        topic.setStatus(Topic.TopicStatus.ACTIVE);
        topic = topicRepository.save(topic);

        log.info("Created topic {} with {} partitions", name, partitions.size());
        return topic;
    }

    /**
     * Delete a topic and all its partitions.
     */
    @Transactional
    public void deleteTopic(String name) {
        log.info("Deleting topic: {}", name);

        Topic topic = topicRepository.findByName(name)
                .orElseThrow(() -> new TopicNotFoundException("Topic not found: " + name));

        // Check if it's an internal topic
        if (topic.getIsInternal()) {
            throw new IllegalArgumentException("Cannot delete internal topic: " + name);
        }

        // Mark topic as deleting
        topic.setStatus(Topic.TopicStatus.DELETING);
        topicRepository.save(topic);

        // Delete all partitions
        List<Partition> partitions = partitionRepository.findByTopicName(name);
        partitionRepository.deleteAll(partitions);

        // Delete topic
        topicRepository.delete(topic);

        log.info("Deleted topic: {}", name);
    }

    /**
     * Get topic by name.
     */
    @Transactional(readOnly = true)
    public Topic getTopic(String name) {
        return topicRepository.findByName(name)
                .orElseThrow(() -> new TopicNotFoundException("Topic not found: " + name));
    }

    /**
     * Get all topics (excluding internal).
     */
    @Transactional(readOnly = true)
    public List<Topic> getAllTopics() {
        return topicRepository.findAllUserTopics();
    }

    /**
     * Get all topics including internal.
     */
    @Transactional(readOnly = true)
    public List<Topic> getAllTopicsIncludingInternal() {
        return topicRepository.findAll();
    }

    /**
     * Get partitions for a topic.
     */
    @Transactional(readOnly = true)
    public List<Partition> getPartitions(String topicName) {
        return partitionRepository.findByTopicName(topicName);
    }

    /**
     * Get a specific partition.
     */
    @Transactional(readOnly = true)
    public Partition getPartition(String topicName, int partitionNumber) {
        return partitionRepository.findByTopicNameAndPartitionNumber(topicName, partitionNumber)
                .orElseThrow(() -> new PartitionNotFoundException(
                        String.format("Partition %d not found for topic %s", partitionNumber, topicName)));
    }

    /**
     * Increase the number of partitions for a topic.
     * Note: Kafka does not support decreasing partitions.
     */
    @Transactional
    public List<Partition> increasePartitions(String topicName, int newPartitionCount) {
        Topic topic = getTopic(topicName);
        int currentCount = topic.getPartitionCount();

        if (newPartitionCount <= currentCount) {
            throw new IllegalArgumentException(
                    String.format("New partition count %d must be greater than current %d",
                            newPartitionCount, currentCount));
        }

        log.info("Increasing partitions for topic {} from {} to {}",
                topicName, currentCount, newPartitionCount);

        // Create new partitions
        List<Partition> newPartitions = new ArrayList<>();
        for (int i = currentCount; i < newPartitionCount; i++) {
            Partition partition = createPartition(topic, i);
            newPartitions.add(partition);
        }

        // Update topic
        topic.setPartitionCount(newPartitionCount);
        topicRepository.save(topic);

        log.info("Added {} new partitions to topic {}", newPartitions.size(), topicName);
        return newPartitions;
    }

    /**
     * Update topic configuration.
     */
    @Transactional
    public Topic updateConfig(String topicName, Map<String, String> configs) {
        Topic topic = getTopic(topicName);
        applyTopicConfigs(topic, configs);
        return topicRepository.save(topic);
    }

    /**
     * Get topic metadata including partition info.
     */
    @Transactional(readOnly = true)
    public TopicMetadata getTopicMetadata(String topicName) {
        Topic topic = getTopic(topicName);
        List<Partition> partitions = partitionRepository.findByTopicName(topicName);

        List<PartitionMetadata> partitionMetadata = partitions.stream()
                .map(p -> new PartitionMetadata(
                        p.getPartitionNumber(),
                        p.getLeaderBrokerId(),
                        p.getReplicaBrokerIds(),
                        p.getIsrBrokerIds(),
                        p.getLogEndOffset(),
                        p.getHighWatermark()))
                .toList();

        return new TopicMetadata(
                topic.getName(),
                topic.getIsInternal(),
                partitionMetadata);
    }

    /**
     * Create partitions for a new topic.
     */
    private List<Partition> createPartitions(Topic topic, int numPartitions, int replicationFactor) {
        List<Partition> partitions = new ArrayList<>();

        for (int i = 0; i < numPartitions; i++) {
            Partition partition = createPartition(topic, i);
            partitions.add(partition);
        }

        return partitions;
    }

    /**
     * Create a single partition with replica assignments.
     */
    private Partition createPartition(Topic topic, int partitionNumber) {
        Partition partition = new Partition();
        partition.setTopic(topic);
        partition.setPartitionNumber(partitionNumber);

        // Get replica assignments from assignment service
        List<Integer> replicas = replicaAssignmentService.assignReplicas(
                topic.getName(), partitionNumber, topic.getReplicationFactor());

        if (replicas.isEmpty()) {
            throw new InsufficientBrokersException(
                    "Not enough brokers for replication factor " + topic.getReplicationFactor());
        }

        // First replica is the leader
        partition.setLeaderBrokerId(replicas.get(0));
        partition.setReplicaBrokerIds(new ArrayList<>(replicas));
        partition.setIsrBrokerIds(new ArrayList<>(replicas)); // Initially all replicas are in-sync
        partition.setLeaderEpoch(0);
        partition.setStatus(Partition.PartitionStatus.ONLINE);

        return partitionRepository.save(partition);
    }

    /**
     * Apply configuration settings to a topic.
     */
    private void applyTopicConfigs(Topic topic, Map<String, String> configs) {
        if (configs == null)
            return;

        configs.forEach((key, value) -> {
            switch (key) {
                case "retention.ms" -> topic.setRetentionMs(Long.parseLong(value));
                case "retention.bytes" -> topic.setRetentionBytes(Long.parseLong(value));
                case "segment.bytes" -> topic.setSegmentBytes(Long.parseLong(value));
                case "segment.ms" -> topic.setSegmentMs(Long.parseLong(value));
                case "cleanup.policy" -> topic.setCleanupPolicy(Topic.CleanupPolicy.valueOf(value.toUpperCase()));
                case "compression.type" -> topic.setCompressionType(Topic.CompressionType.valueOf(value.toUpperCase()));
                case "min.insync.replicas" -> topic.setMinInsyncReplicas(Integer.parseInt(value));
                case "max.message.bytes" -> topic.setMaxMessageBytes(Integer.parseInt(value));
                case "message.timestamp.type" ->
                    topic.setMessageTimestampType(Topic.TimestampType.valueOf(value.toUpperCase()));
                default -> log.warn("Unknown topic config: {}", key);
            }
        });
    }

    /**
     * Validate topic name follows Kafka naming conventions.
     */
    private void validateTopicName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Topic name cannot be empty");
        }
        if (name.length() > 249) {
            throw new IllegalArgumentException("Topic name cannot exceed 249 characters");
        }
        if (!name.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException(
                    "Topic name can only contain alphanumeric characters, '.', '_', and '-'");
        }
        if (name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Topic name cannot be '.' or '..'");
        }
    }

    // DTOs
    public record TopicMetadata(
            String name,
            boolean internal,
            List<PartitionMetadata> partitions) {
    }

    public record PartitionMetadata(
            int partition,
            Integer leader,
            List<Integer> replicas,
            List<Integer> isr,
            long logEndOffset,
            long highWatermark) {
    }

    // Exceptions
    public static class TopicNotFoundException extends RuntimeException {
        public TopicNotFoundException(String message) {
            super(message);
        }
    }

    public static class TopicAlreadyExistsException extends RuntimeException {
        public TopicAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class PartitionNotFoundException extends RuntimeException {
        public PartitionNotFoundException(String message) {
            super(message);
        }
    }

    public static class InsufficientBrokersException extends RuntimeException {
        public InsufficientBrokersException(String message) {
            super(message);
        }
    }
}
