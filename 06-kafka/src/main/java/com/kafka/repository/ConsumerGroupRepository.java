package com.kafka.repository;

import com.kafka.entity.ConsumerGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumerGroupRepository extends JpaRepository<ConsumerGroup, UUID> {

    Optional<ConsumerGroup> findByGroupId(String groupId);

    boolean existsByGroupId(String groupId);

    List<ConsumerGroup> findByState(ConsumerGroup.GroupState state);

    @Query("SELECT g FROM ConsumerGroup g WHERE g.coordinatorBrokerId = :brokerId")
    List<ConsumerGroup> findByCoordinator(Integer brokerId);

    @Query("SELECT g FROM ConsumerGroup g WHERE :topic MEMBER OF g.subscribedTopics")
    List<ConsumerGroup> findBySubscribedTopic(String topic);

    @Query("SELECT g FROM ConsumerGroup g WHERE g.state = 'STABLE' AND g.lastHeartbeat < :threshold")
    List<ConsumerGroup> findStaleGroups(Instant threshold);

    @Modifying
    @Query("UPDATE ConsumerGroup g SET g.state = :state, g.generation = g.generation + 1 " +
            "WHERE g.groupId = :groupId")
    int updateStateAndIncrementGeneration(String groupId, ConsumerGroup.GroupState state);

    @Modifying
    @Query("UPDATE ConsumerGroup g SET g.lastHeartbeat = :timestamp WHERE g.groupId = :groupId")
    int updateHeartbeat(String groupId, Instant timestamp);

    @Modifying
    @Query("UPDATE ConsumerGroup g SET g.leaderId = :leaderId WHERE g.groupId = :groupId")
    int updateLeader(String groupId, String leaderId);

    @Modifying
    @Query("UPDATE ConsumerGroup g SET g.coordinatorBrokerId = :brokerId WHERE g.groupId = :groupId")
    int updateCoordinator(String groupId, Integer brokerId);

    @Query("SELECT COUNT(g) FROM ConsumerGroup g WHERE g.state = 'STABLE'")
    long countStableGroups();

    @Query("SELECT COUNT(g) FROM ConsumerGroup g WHERE g.coordinatorBrokerId = :brokerId")
    int countGroupsByCoordinator(Integer brokerId);
}
