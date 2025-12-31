package com.kafka.service;

import com.kafka.entity.Broker;
import com.kafka.repository.BrokerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service responsible for assigning replicas to partitions.
 * Implements rack-aware replica assignment to ensure fault tolerance.
 * 
 * Key considerations:
 * 1. Spread replicas across different brokers
 * 2. Spread replicas across different racks (if rack-aware)
 * 3. Balance partition leadership across brokers
 * 4. Minimize data movement during reassignment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplicaAssignmentService {

    private final BrokerRepository brokerRepository;

    // Track leader count per broker for balancing
    private final Map<Integer, AtomicInteger> leaderCount = new ConcurrentHashMap<>();

    // Track replica count per broker for balancing
    private final Map<Integer, AtomicInteger> replicaCount = new ConcurrentHashMap<>();

    /**
     * Assign replicas for a new partition.
     * Uses round-robin with rack awareness.
     */
    public List<Integer> assignReplicas(String topicName, int partitionId, int replicationFactor) {
        List<Broker> onlineBrokers = brokerRepository.findOnlineBrokers();

        if (onlineBrokers.size() < replicationFactor) {
            log.error("Not enough online brokers ({}) for replication factor {}",
                    onlineBrokers.size(), replicationFactor);
            return Collections.emptyList();
        }

        // Check if rack-aware assignment is possible
        List<String> distinctRacks = brokerRepository.findDistinctRacks();
        boolean rackAware = distinctRacks.size() > 1;

        if (rackAware) {
            return assignReplicasRackAware(onlineBrokers, topicName, partitionId, replicationFactor);
        } else {
            return assignReplicasSimple(onlineBrokers, topicName, partitionId, replicationFactor);
        }
    }

    /**
     * Simple round-robin replica assignment (non-rack-aware).
     * 
     * Algorithm:
     * 1. Sort brokers by current replica count
     * 2. Select first broker as leader (with fewest leaders)
     * 3. Assign remaining replicas in round-robin fashion
     */
    private List<Integer> assignReplicasSimple(List<Broker> brokers, String topicName,
            int partitionId, int replicationFactor) {
        List<Integer> assignment = new ArrayList<>();

        // Sort brokers by current load (leader count, then replica count)
        List<Broker> sortedBrokers = brokers.stream()
                .sorted(Comparator
                        .comparingInt((Broker b) -> getLeaderCount(b.getBrokerId()))
                        .thenComparingInt(b -> getReplicaCount(b.getBrokerId())))
                .toList();

        // Calculate starting index based on topic and partition for distribution
        int startIndex = Math.abs((topicName.hashCode() + partitionId)) % sortedBrokers.size();

        // Assign replicas
        for (int i = 0; i < replicationFactor; i++) {
            int brokerIndex = (startIndex + i) % sortedBrokers.size();
            int brokerId = sortedBrokers.get(brokerIndex).getBrokerId();
            assignment.add(brokerId);
            incrementReplicaCount(brokerId);
            if (i == 0) {
                incrementLeaderCount(brokerId);
            }
        }

        log.debug("Assigned replicas for {}-{}: {}", topicName, partitionId, assignment);
        return assignment;
    }

    /**
     * Rack-aware replica assignment.
     * 
     * Algorithm:
     * 1. Group brokers by rack
     * 2. Select leader from rack with fewest leaders
     * 3. Spread remaining replicas across different racks
     * 4. If not enough racks, use multiple brokers from same rack
     */
    private List<Integer> assignReplicasRackAware(List<Broker> brokers, String topicName,
            int partitionId, int replicationFactor) {
        List<Integer> assignment = new ArrayList<>();
        Set<Integer> usedBrokers = new HashSet<>();
        Set<String> usedRacks = new HashSet<>();

        // Group brokers by rack
        Map<String, List<Broker>> brokersByRack = brokers.stream()
                .collect(Collectors.groupingBy(b -> b.getRack() != null ? b.getRack() : "default"));

        // Sort racks by total leader count
        List<String> sortedRacks = brokersByRack.keySet().stream()
                .sorted(Comparator.comparingInt(rack -> brokersByRack.get(rack).stream()
                        .mapToInt(b -> getLeaderCount(b.getBrokerId()))
                        .sum()))
                .toList();

        int rackIndex = Math.abs(topicName.hashCode() + partitionId) % sortedRacks.size();

        // First pass: one replica per rack
        for (int i = 0; i < replicationFactor && assignment.size() < replicationFactor; i++) {
            String rack = sortedRacks.get((rackIndex + i) % sortedRacks.size());

            // Skip if rack already used and we have more racks to try
            if (usedRacks.contains(rack) && usedRacks.size() < sortedRacks.size()) {
                continue;
            }

            // Find best broker in this rack
            Optional<Broker> bestBroker = brokersByRack.get(rack).stream()
                    .filter(b -> !usedBrokers.contains(b.getBrokerId()))
                    .min(Comparator.comparingInt(b -> getReplicaCount(b.getBrokerId())));

            if (bestBroker.isPresent()) {
                int brokerId = bestBroker.get().getBrokerId();
                assignment.add(brokerId);
                usedBrokers.add(brokerId);
                usedRacks.add(rack);
                incrementReplicaCount(brokerId);
                if (assignment.size() == 1) {
                    incrementLeaderCount(brokerId);
                }
            }
        }

        // Second pass: if we still need more replicas, allow multiple per rack
        while (assignment.size() < replicationFactor) {
            String rack = sortedRacks.get((rackIndex + assignment.size()) % sortedRacks.size());

            Optional<Broker> broker = brokersByRack.get(rack).stream()
                    .filter(b -> !usedBrokers.contains(b.getBrokerId()))
                    .min(Comparator.comparingInt(b -> getReplicaCount(b.getBrokerId())));

            if (broker.isPresent()) {
                int brokerId = broker.get().getBrokerId();
                assignment.add(brokerId);
                usedBrokers.add(brokerId);
                incrementReplicaCount(brokerId);
            } else {
                // No more unique brokers available
                log.warn("Could not assign {} replicas, only {} available",
                        replicationFactor, assignment.size());
                break;
            }
        }

        log.debug("Rack-aware assigned replicas for {}-{}: {}", topicName, partitionId, assignment);
        return assignment;
    }

    /**
     * Reassign replicas for partition movement/rebalancing.
     * Tries to minimize data movement while achieving better balance.
     */
    public List<Integer> reassignReplicas(String topicName, int partitionId,
            List<Integer> currentReplicas, int replicationFactor) {
        List<Broker> onlineBrokers = brokerRepository.findOnlineBrokers();
        Set<Integer> onlineBrokerIds = onlineBrokers.stream()
                .map(Broker::getBrokerId)
                .collect(Collectors.toSet());

        // Keep replicas that are still online
        List<Integer> validReplicas = currentReplicas.stream()
                .filter(onlineBrokerIds::contains)
                .toList();

        if (validReplicas.size() >= replicationFactor) {
            // All replicas are online, just return current assignment
            return new ArrayList<>(validReplicas.subList(0, replicationFactor));
        }

        // Need to add new replicas
        List<Integer> newAssignment = new ArrayList<>(validReplicas);
        Set<Integer> usedBrokers = new HashSet<>(validReplicas);

        // Add new replicas from least loaded brokers
        List<Broker> availableBrokers = onlineBrokers.stream()
                .filter(b -> !usedBrokers.contains(b.getBrokerId()))
                .sorted(Comparator.comparingInt(b -> getReplicaCount(b.getBrokerId())))
                .toList();

        for (Broker broker : availableBrokers) {
            if (newAssignment.size() >= replicationFactor)
                break;
            newAssignment.add(broker.getBrokerId());
            incrementReplicaCount(broker.getBrokerId());
        }

        // Update leader count if leader changed
        if (!newAssignment.isEmpty() &&
                (currentReplicas.isEmpty() || !newAssignment.get(0).equals(currentReplicas.get(0)))) {
            incrementLeaderCount(newAssignment.get(0));
            if (!currentReplicas.isEmpty()) {
                decrementLeaderCount(currentReplicas.get(0));
            }
        }

        return newAssignment;
    }

    /**
     * Calculate preferred replica election to rebalance leadership.
     */
    public Map<String, Map<Integer, Integer>> preferredReplicaElection(List<String> topics) {
        Map<String, Map<Integer, Integer>> elections = new HashMap<>();

        for (String topic : topics) {
            Map<Integer, Integer> topicElections = new HashMap<>();
            // Logic would go here to determine which partitions need leader change
            // to match their preferred (first) replica
            elections.put(topic, topicElections);
        }

        return elections;
    }

    /**
     * Get assignment balance metrics.
     */
    public AssignmentBalance getAssignmentBalance() {
        List<Broker> brokers = brokerRepository.findOnlineBrokers();

        int totalLeaders = leaderCount.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();
        int totalReplicas = replicaCount.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();

        double avgLeaders = brokers.isEmpty() ? 0 : (double) totalLeaders / brokers.size();
        double avgReplicas = brokers.isEmpty() ? 0 : (double) totalReplicas / brokers.size();

        // Calculate standard deviation
        double leaderVariance = brokers.stream()
                .mapToDouble(b -> Math.pow(getLeaderCount(b.getBrokerId()) - avgLeaders, 2))
                .average()
                .orElse(0);

        double replicaVariance = brokers.stream()
                .mapToDouble(b -> Math.pow(getReplicaCount(b.getBrokerId()) - avgReplicas, 2))
                .average()
                .orElse(0);

        return new AssignmentBalance(
                totalLeaders,
                totalReplicas,
                avgLeaders,
                avgReplicas,
                Math.sqrt(leaderVariance),
                Math.sqrt(replicaVariance));
    }

    private int getLeaderCount(Integer brokerId) {
        return leaderCount.computeIfAbsent(brokerId, k -> new AtomicInteger(0)).get();
    }

    private int getReplicaCount(Integer brokerId) {
        return replicaCount.computeIfAbsent(brokerId, k -> new AtomicInteger(0)).get();
    }

    private void incrementLeaderCount(Integer brokerId) {
        leaderCount.computeIfAbsent(brokerId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void decrementLeaderCount(Integer brokerId) {
        leaderCount.computeIfAbsent(brokerId, k -> new AtomicInteger(0)).decrementAndGet();
    }

    private void incrementReplicaCount(Integer brokerId) {
        replicaCount.computeIfAbsent(brokerId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public record AssignmentBalance(
            int totalLeaders,
            int totalReplicas,
            double avgLeadersPerBroker,
            double avgReplicasPerBroker,
            double leaderStdDev,
            double replicaStdDev) {
    }
}
