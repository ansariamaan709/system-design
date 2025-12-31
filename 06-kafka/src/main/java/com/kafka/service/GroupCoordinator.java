package com.kafka.service;

import com.kafka.entity.ConsumerGroup;
import com.kafka.entity.ConsumerGroupMember;
import com.kafka.entity.Partition;
import com.kafka.repository.ConsumerGroupMemberRepository;
import com.kafka.repository.ConsumerGroupRepository;
import com.kafka.repository.PartitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Group Coordinator - Manages consumer group membership and partition
 * assignment.
 * 
 * Key responsibilities:
 * 1. Consumer group management (join, leave, heartbeat)
 * 2. Partition assignment strategies (Range, RoundRobin, Sticky)
 * 3. Rebalance coordination
 * 4. Member failure detection
 * 
 * Rebalance Protocol:
 * 1. Member sends JoinGroup request
 * 2. Coordinator waits for all members
 * 3. Leader computes assignment
 * 4. Members sync assignment
 * 5. Group becomes STABLE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupCoordinator {

    private final ConsumerGroupRepository groupRepository;
    private final ConsumerGroupMemberRepository memberRepository;
    private final PartitionRepository partitionRepository;
    private final TopicService topicService;

    @Value("${kafka.group.session.timeout.ms:45000}")
    private int sessionTimeoutMs;

    @Value("${kafka.group.rebalance.timeout.ms:300000}")
    private int rebalanceTimeoutMs;

    @Value("${kafka.group.heartbeat.interval.ms:3000}")
    private int heartbeatIntervalMs;

    // Pending join requests during rebalance
    private final ConcurrentMap<String, List<JoinGroupRequest>> pendingJoins = new ConcurrentHashMap<>();

    // Delayed join completions
    private final ConcurrentMap<String, CompletableFuture<JoinGroupResult>> delayedJoins = new ConcurrentHashMap<>();

    /**
     * Handle JoinGroup request from a consumer.
     * This is phase 1 of the rebalance protocol.
     */
    @Transactional
    public JoinGroupResult joinGroup(JoinGroupRequest request) {
        String groupId = request.groupId();
        String memberId = request.memberId();

        log.info("JoinGroup request: group={}, member={}, protocol={}",
                groupId, memberId, request.protocolType());

        // Get or create group
        ConsumerGroup group = groupRepository.findByGroupId(groupId)
                .orElseGet(() -> createGroup(groupId, request.protocolType()));

        // Validate protocol type
        if (!group.getProtocolType().equals(request.protocolType())) {
            return JoinGroupResult.error(JoinGroupResult.ErrorCode.INCONSISTENT_GROUP_PROTOCOL);
        }

        // Generate member ID if empty
        if (memberId == null || memberId.isEmpty()) {
            memberId = generateMemberId(request.clientId());
        }

        // Handle based on group state
        return switch (group.getState()) {
            case EMPTY -> handleJoinEmpty(group, request, memberId);
            case PREPARING_REBALANCE -> handleJoinPreparing(group, request, memberId);
            case COMPLETING_REBALANCE -> handleJoinCompleting(group, request, memberId);
            case STABLE -> handleJoinStable(group, request, memberId);
            case DEAD -> JoinGroupResult.error(JoinGroupResult.ErrorCode.COORDINATOR_NOT_AVAILABLE);
        };
    }

    /**
     * Handle SyncGroup request from a consumer.
     * This is phase 2 of the rebalance protocol.
     */
    @Transactional
    public SyncGroupResult syncGroup(SyncGroupRequest request) {
        String groupId = request.groupId();
        String memberId = request.memberId();

        log.info("SyncGroup request: group={}, member={}, generation={}",
                groupId, memberId, request.generation());

        ConsumerGroup group = groupRepository.findByGroupId(groupId)
                .orElse(null);

        if (group == null) {
            return SyncGroupResult.error(SyncGroupResult.ErrorCode.UNKNOWN_GROUP);
        }

        // Validate generation
        if (request.generation() != group.getGenerationId()) {
            return SyncGroupResult.error(SyncGroupResult.ErrorCode.ILLEGAL_GENERATION);
        }

        // Validate member
        Optional<ConsumerGroupMember> memberOpt = memberRepository.findByGroupGroupIdAndMemberId(groupId, memberId);
        if (memberOpt.isEmpty()) {
            return SyncGroupResult.error(SyncGroupResult.ErrorCode.UNKNOWN_MEMBER);
        }

        // If this member is the leader, save the assignment
        if (memberId.equals(group.getLeaderMemberId()) && request.assignments() != null) {
            saveAssignments(groupId, request.assignments());
        }

        // Get this member's assignment
        ConsumerGroupMember member = memberOpt.get();
        String assignment = member.getAssignedPartitions();

        // If group is now stable, return assignment
        if (group.getState() == ConsumerGroup.GroupState.COMPLETING_REBALANCE) {
            group.setState(ConsumerGroup.GroupState.STABLE);
            groupRepository.save(group);
        }

        return new SyncGroupResult(
                SyncGroupResult.ErrorCode.NONE,
                assignment != null ? parseAssignment(assignment) : Collections.emptyMap());
    }

    /**
     * Handle heartbeat from a consumer.
     */
    @Transactional
    public HeartbeatResult heartbeat(String groupId, String memberId, int generation) {
        ConsumerGroup group = groupRepository.findByGroupId(groupId).orElse(null);
        if (group == null) {
            return new HeartbeatResult(HeartbeatResult.ErrorCode.UNKNOWN_GROUP, null);
        }

        // Validate generation
        if (generation != group.getGenerationId()) {
            return new HeartbeatResult(
                    HeartbeatResult.ErrorCode.REBALANCE_IN_PROGRESS,
                    "Rebalance triggered");
        }

        // Update heartbeat
        memberRepository.updateHeartbeat(memberId, Instant.now());
        groupRepository.updateHeartbeat(groupId, Instant.now());

        // Check if rebalance is needed
        if (group.getState() == ConsumerGroup.GroupState.PREPARING_REBALANCE) {
            return new HeartbeatResult(
                    HeartbeatResult.ErrorCode.REBALANCE_IN_PROGRESS,
                    "Group is rebalancing");
        }

        return new HeartbeatResult(HeartbeatResult.ErrorCode.NONE, null);
    }

    /**
     * Trigger a rebalance for a consumer group.
     */
    @Transactional
    public void triggerRebalance(String groupId) {
        log.info("Triggering rebalance for group {}", groupId);

        ConsumerGroup group = groupRepository.findByGroupId(groupId).orElse(null);
        if (group == null)
            return;

        group.setState(ConsumerGroup.GroupState.PREPARING_REBALANCE);
        groupRepository.save(group);
    }

    /**
     * Scheduled task to detect failed members.
     */
    @Scheduled(fixedDelayString = "${kafka.group.heartbeat.interval.ms:3000}")
    @Transactional
    public void detectFailedMembers() {
        Instant threshold = Instant.now().minusMillis(sessionTimeoutMs);
        List<ConsumerGroup> stableGroups = groupRepository.findByState(ConsumerGroup.GroupState.STABLE);

        for (ConsumerGroup group : stableGroups) {
            List<ConsumerGroupMember> staleMembers = memberRepository.findStaleMembers(group.getGroupId(), threshold);

            if (!staleMembers.isEmpty()) {
                log.warn("Detected {} failed members in group {}",
                        staleMembers.size(), group.getGroupId());

                // Remove failed members
                for (ConsumerGroupMember member : staleMembers) {
                    memberRepository.delete(member);
                }

                // Trigger rebalance
                triggerRebalance(group.getGroupId());
            }
        }
    }

    /**
     * Perform partition assignment using the configured strategy.
     */
    public Map<String, List<TopicPartition>> performAssignment(
            String groupId, String strategy, List<MemberSubscription> subscriptions) {

        log.info("Performing {} assignment for group {} with {} members",
                strategy, groupId, subscriptions.size());

        // Collect all topics
        Set<String> allTopics = subscriptions.stream()
                .flatMap(s -> s.topics().stream())
                .collect(Collectors.toSet());

        // Get partitions for all topics
        Map<String, List<Integer>> topicPartitions = new HashMap<>();
        for (String topic : allTopics) {
            List<Partition> partitions = partitionRepository.findByTopicName(topic);
            topicPartitions.put(topic, partitions.stream()
                    .map(Partition::getPartitionNumber)
                    .sorted()
                    .toList());
        }

        // Perform assignment based on strategy
        return switch (strategy.toLowerCase()) {
            case "range" -> assignRange(subscriptions, topicPartitions);
            case "roundrobin" -> assignRoundRobin(subscriptions, topicPartitions);
            case "sticky" -> assignSticky(subscriptions, topicPartitions);
            default -> assignRange(subscriptions, topicPartitions);
        };
    }

    /**
     * Range assignment strategy.
     * Assigns partitions of each topic independently using ranges.
     */
    private Map<String, List<TopicPartition>> assignRange(
            List<MemberSubscription> subscriptions,
            Map<String, List<Integer>> topicPartitions) {

        Map<String, List<TopicPartition>> assignment = new HashMap<>();
        for (MemberSubscription sub : subscriptions) {
            assignment.put(sub.memberId(), new ArrayList<>());
        }

        for (Map.Entry<String, List<Integer>> entry : topicPartitions.entrySet()) {
            String topic = entry.getKey();
            List<Integer> partitions = entry.getValue();

            // Get members subscribed to this topic
            List<String> subscribedMembers = subscriptions.stream()
                    .filter(s -> s.topics().contains(topic))
                    .map(MemberSubscription::memberId)
                    .sorted()
                    .toList();

            if (subscribedMembers.isEmpty())
                continue;

            // Divide partitions among members
            int partitionsPerMember = partitions.size() / subscribedMembers.size();
            int extra = partitions.size() % subscribedMembers.size();
            int partitionIndex = 0;

            for (int i = 0; i < subscribedMembers.size(); i++) {
                String memberId = subscribedMembers.get(i);
                int count = partitionsPerMember + (i < extra ? 1 : 0);

                for (int j = 0; j < count && partitionIndex < partitions.size(); j++) {
                    assignment.get(memberId).add(
                            new TopicPartition(topic, partitions.get(partitionIndex++)));
                }
            }
        }

        return assignment;
    }

    /**
     * Round-robin assignment strategy.
     * Assigns all partitions in a round-robin fashion.
     */
    private Map<String, List<TopicPartition>> assignRoundRobin(
            List<MemberSubscription> subscriptions,
            Map<String, List<Integer>> topicPartitions) {

        Map<String, List<TopicPartition>> assignment = new HashMap<>();
        for (MemberSubscription sub : subscriptions) {
            assignment.put(sub.memberId(), new ArrayList<>());
        }

        // Create list of all partitions
        List<TopicPartition> allPartitions = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : topicPartitions.entrySet()) {
            for (Integer partition : entry.getValue()) {
                allPartitions.add(new TopicPartition(entry.getKey(), partition));
            }
        }

        // Sort for determinism
        allPartitions.sort(Comparator
                .comparing(TopicPartition::topic)
                .thenComparingInt(TopicPartition::partition));

        // Assign round-robin
        List<String> memberIds = subscriptions.stream()
                .map(MemberSubscription::memberId)
                .sorted()
                .toList();

        int memberIndex = 0;
        for (TopicPartition tp : allPartitions) {
            // Find next member subscribed to this topic
            int checked = 0;
            while (checked < memberIds.size()) {
                String memberId = memberIds.get(memberIndex % memberIds.size());
                MemberSubscription sub = subscriptions.stream()
                        .filter(s -> s.memberId().equals(memberId))
                        .findFirst()
                        .orElse(null);

                if (sub != null && sub.topics().contains(tp.topic())) {
                    assignment.get(memberId).add(tp);
                    memberIndex++;
                    break;
                }
                memberIndex++;
                checked++;
            }
        }

        return assignment;
    }

    /**
     * Sticky assignment strategy.
     * Tries to minimize partition movement during rebalances.
     */
    private Map<String, List<TopicPartition>> assignSticky(
            List<MemberSubscription> subscriptions,
            Map<String, List<Integer>> topicPartitions) {

        // For simplicity, start with round-robin and then optimize
        // Full sticky assignor is more complex
        return assignRoundRobin(subscriptions, topicPartitions);
    }

    private ConsumerGroup createGroup(String groupId, String protocolType) {
        ConsumerGroup group = new ConsumerGroup();
        group.setGroupId(groupId);
        group.setProtocolType(protocolType);
        group.setState(ConsumerGroup.GroupState.EMPTY);
        group.setGenerationId(0);
        return groupRepository.save(group);
    }

    private String generateMemberId(String clientId) {
        return clientId + "-" + UUID.randomUUID();
    }

    private JoinGroupResult handleJoinEmpty(ConsumerGroup group, JoinGroupRequest request, String memberId) {
        // First member - make them leader
        group.setState(ConsumerGroup.GroupState.PREPARING_REBALANCE);
        group.setLeaderMemberId(memberId);
        group.setGenerationId(group.getGenerationId() + 1);
        groupRepository.save(group);

        // Register member
        registerMember(group, memberId, request);

        log.info("First member {} joined group {}, starting rebalance", memberId, group.getGroupId());

        return new JoinGroupResult(
                JoinGroupResult.ErrorCode.NONE,
                group.getGenerationId(),
                group.getProtocol(),
                memberId,
                memberId, // This member is the leader
                Collections.emptyList() // Members will be populated in sync
        );
    }

    private JoinGroupResult handleJoinPreparing(ConsumerGroup group, JoinGroupRequest request, String memberId) {
        // Register member
        registerMember(group, memberId, request);

        // Add to pending joins
        pendingJoins.computeIfAbsent(group.getGroupId(), k -> new CopyOnWriteArrayList<>())
                .add(request);

        // Return result
        boolean isLeader = memberId.equals(group.getLeaderMemberId());
        List<MemberSubscription> members = isLeader ? getMemberSubscriptions(group.getGroupId())
                : Collections.emptyList();

        return new JoinGroupResult(
                JoinGroupResult.ErrorCode.NONE,
                group.getGenerationId(),
                group.getProtocol(),
                memberId,
                group.getLeaderMemberId(),
                members);
    }

    private JoinGroupResult handleJoinCompleting(ConsumerGroup group, JoinGroupRequest request, String memberId) {
        // Trigger new rebalance
        group.setState(ConsumerGroup.GroupState.PREPARING_REBALANCE);
        group.setGenerationId(group.getGenerationId() + 1);
        groupRepository.save(group);

        return handleJoinPreparing(group, request, memberId);
    }

    private JoinGroupResult handleJoinStable(ConsumerGroup group, JoinGroupRequest request, String memberId) {
        // Check if this is an existing member with same subscription
        Optional<ConsumerGroupMember> existing = memberRepository.findByGroupGroupIdAndMemberId(group.getGroupId(),
                memberId);

        if (existing.isPresent()) {
            // Update heartbeat and return current assignment
            memberRepository.updateHeartbeat(memberId, Instant.now());

            return new JoinGroupResult(
                    JoinGroupResult.ErrorCode.NONE,
                    group.getGenerationId(),
                    group.getProtocol(),
                    memberId,
                    group.getLeaderMemberId(),
                    Collections.emptyList());
        }

        // New member - trigger rebalance
        group.setState(ConsumerGroup.GroupState.PREPARING_REBALANCE);
        group.setGenerationId(group.getGenerationId() + 1);
        groupRepository.save(group);

        return handleJoinPreparing(group, request, memberId);
    }

    private void registerMember(ConsumerGroup group, String memberId, JoinGroupRequest request) {
        ConsumerGroupMember member = memberRepository
                .findByGroupGroupIdAndMemberId(group.getGroupId(), memberId)
                .orElseGet(ConsumerGroupMember::new);

        member.setGroupId(group.getGroupId());
        member.setMemberId(memberId);
        member.setClientId(request.clientId());
        member.setClientHost(request.clientHost());
        member.setSessionTimeoutMs(request.sessionTimeoutMs());
        member.setRebalanceTimeoutMs(request.rebalanceTimeoutMs());
        member.setSubscribedTopics(request.protocols().get(0).topics());
        member.setLastHeartbeat(LocalDateTime.now());

        memberRepository.save(member);
    }

    private void saveAssignments(String groupId, Map<String, List<TopicPartition>> assignments) {
        for (Map.Entry<String, List<TopicPartition>> entry : assignments.entrySet()) {
            String memberId = entry.getKey();
            List<TopicPartition> partitions = entry.getValue();

            String assignment = partitions.stream()
                    .map(tp -> tp.topic() + ":" + tp.partition())
                    .collect(Collectors.joining(","));

            memberRepository.updateAssignment(memberId, assignment);
        }
    }

    private Map<String, List<TopicPartition>> parseAssignment(String assignment) {
        if (assignment == null || assignment.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<TopicPartition>> result = new HashMap<>();
        for (String part : assignment.split(",")) {
            String[] tpParts = part.split(":");
            if (tpParts.length == 2) {
                String topic = tpParts[0];
                int partition = Integer.parseInt(tpParts[1]);
                result.computeIfAbsent(topic, k -> new ArrayList<>())
                        .add(new TopicPartition(topic, partition));
            }
        }
        return result;
    }

    private List<MemberSubscription> getMemberSubscriptions(String groupId) {
        return memberRepository.findByGroupGroupId(groupId).stream()
                .map(m -> new MemberSubscription(
                        m.getMemberId(),
                        m.getSubscribedTopics()))
                .toList();
    }

    // DTOs
    public record TopicPartition(String topic, int partition) {
    }

    public record JoinGroupRequest(
            String groupId,
            String memberId,
            String clientId,
            String clientHost,
            int sessionTimeoutMs,
            int rebalanceTimeoutMs,
            String protocolType,
            List<Protocol> protocols) {
        public record Protocol(String name, List<String> topics) {
        }
    }

    public record JoinGroupResult(
            ErrorCode errorCode,
            int generation,
            String protocol,
            String memberId,
            String leaderId,
            List<MemberSubscription> members) {
        public enum ErrorCode {
            NONE,
            UNKNOWN_GROUP,
            UNKNOWN_MEMBER,
            INCONSISTENT_GROUP_PROTOCOL,
            COORDINATOR_NOT_AVAILABLE
        }

        public static JoinGroupResult error(ErrorCode code) {
            return new JoinGroupResult(code, -1, null, null, null, Collections.emptyList());
        }
    }

    public record SyncGroupRequest(
            String groupId,
            String memberId,
            int generation,
            Map<String, List<TopicPartition>> assignments) {
    }

    public record SyncGroupResult(
            ErrorCode errorCode,
            Map<String, List<TopicPartition>> assignment) {
        public enum ErrorCode {
            NONE,
            UNKNOWN_GROUP,
            UNKNOWN_MEMBER,
            ILLEGAL_GENERATION,
            REBALANCE_IN_PROGRESS
        }

        public static SyncGroupResult error(ErrorCode code) {
            return new SyncGroupResult(code, Collections.emptyMap());
        }
    }

    public record MemberSubscription(String memberId, List<String> topics) {
    }

    public record HeartbeatResult(ErrorCode errorCode, String message) {
        public enum ErrorCode {
            NONE,
            UNKNOWN_GROUP,
            UNKNOWN_MEMBER,
            REBALANCE_IN_PROGRESS
        }
    }
}
