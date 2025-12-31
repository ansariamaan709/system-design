package com.kafka.repository;

import com.kafka.entity.Partition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartitionRepository extends JpaRepository<Partition, UUID> {

    List<Partition> findByTopicTopicId(UUID topicId);

    @Query("SELECT p FROM Partition p JOIN p.topic t WHERE t.name = :topicName ORDER BY p.partitionNumber")
    List<Partition> findByTopicName(String topicName);

    @Query("SELECT p FROM Partition p JOIN p.topic t WHERE t.name = :topicName AND p.partitionNumber = :partition")
    Optional<Partition> findByTopicNameAndPartitionNumber(String topicName, int partition);

    List<Partition> findByLeaderBrokerId(Integer brokerId);

    @Query("SELECT p FROM Partition p WHERE :brokerId = ANY(p.isrBrokerIds)")
    List<Partition> findByIsrContaining(Integer brokerId);

    @Query("SELECT p FROM Partition p WHERE :brokerId = ANY(p.replicaBrokerIds)")
    List<Partition> findByReplicasContaining(Integer brokerId);

    @Query("SELECT p FROM Partition p WHERE p.status = 'OFFLINE' OR p.leaderBrokerId IS NULL")
    List<Partition> findOfflinePartitions();

    @Query("SELECT p FROM Partition p WHERE array_length(p.isrBrokerIds, 1) < array_length(p.replicaBrokerIds, 1)")
    List<Partition> findUnderReplicatedPartitions();

    @Modifying
    @Query("UPDATE Partition p SET p.leaderBrokerId = :leaderId, p.leaderEpoch = p.leaderEpoch + 1 " +
            "WHERE p.partitionId = :partitionId")
    int updateLeader(UUID partitionId, Integer leaderId);

    @Modifying
    @Query("UPDATE Partition p SET p.logEndOffset = :leo, p.highWatermark = :hw WHERE p.partitionId = :partitionId")
    int updateOffsets(UUID partitionId, long leo, long hw);

    @Modifying
    @Query("UPDATE Partition p SET p.status = :status WHERE p.partitionId = :partitionId")
    int updateStatus(UUID partitionId, Partition.PartitionStatus status);

    @Query("SELECT COUNT(p) FROM Partition p JOIN p.topic t WHERE t.name = :topicName")
    int countByTopicName(String topicName);

    @Query("SELECT COALESCE(SUM(p.logSizeBytes), 0) FROM Partition p JOIN p.topic t WHERE t.name = :topicName")
    long getTotalSizeByTopic(String topicName);
}
