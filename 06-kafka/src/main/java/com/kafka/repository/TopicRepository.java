package com.kafka.repository;

import com.kafka.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findByName(String name);

    boolean existsByName(String name);

    List<Topic> findByStatus(Topic.TopicStatus status);

    List<Topic> findByIsInternalFalseAndStatus(Topic.TopicStatus status);

    @Query("SELECT t FROM Topic t WHERE t.isInternal = false ORDER BY t.name")
    List<Topic> findAllUserTopics();

    @Query("SELECT t FROM Topic t WHERE t.cleanupPolicy IN ('COMPACT', 'DELETE_COMPACT')")
    List<Topic> findCompactedTopics();

    @Modifying
    @Query("UPDATE Topic t SET t.status = :status WHERE t.name = :name")
    int updateStatus(String name, Topic.TopicStatus status);

    @Modifying
    @Query("UPDATE Topic t SET t.partitionCount = :partitions WHERE t.name = :name")
    int updatePartitionCount(String name, int partitions);
}
