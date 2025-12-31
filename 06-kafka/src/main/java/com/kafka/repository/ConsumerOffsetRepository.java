package com.kafka.repository;

import com.kafka.entity.ConsumerOffset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumerOffsetRepository extends JpaRepository<ConsumerOffset, UUID> {

    @Query("SELECT o FROM ConsumerOffset o WHERE o.groupId = :groupId AND o.topic = :topic AND o.partition = :partition")
    Optional<ConsumerOffset> findByGroupTopicPartition(String groupId, String topic, int partition);

    @Query("SELECT o FROM ConsumerOffset o WHERE o.groupId = :groupId AND o.topic = :topic ORDER BY o.partition")
    List<ConsumerOffset> findByGroupAndTopic(String groupId, String topic);

    @Query("SELECT o FROM ConsumerOffset o WHERE o.groupId = :groupId ORDER BY o.topic, o.partition")
    List<ConsumerOffset> findByGroupId(String groupId);

    @Query("SELECT o FROM ConsumerOffset o WHERE o.topic = :topic AND o.partition = :partition")
    List<ConsumerOffset> findByTopicPartition(String topic, int partition);

    @Modifying
    @Query("UPDATE ConsumerOffset o SET o.committedOffset = :offset, o.metadata = :metadata, " +
            "o.commitTimestamp = :timestamp, o.leaderEpoch = :leaderEpoch " +
            "WHERE o.groupId = :groupId AND o.topic = :topic AND o.partition = :partition")
    int updateOffset(String groupId, String topic, int partition, long offset,
            String metadata, Instant timestamp, Integer leaderEpoch);

    @Modifying
    @Query("DELETE FROM ConsumerOffset o WHERE o.groupId = :groupId")
    int deleteByGroupId(String groupId);

    @Modifying
    @Query("DELETE FROM ConsumerOffset o WHERE o.topic = :topic")
    int deleteByTopic(String topic);

    @Modifying
    @Query("DELETE FROM ConsumerOffset o WHERE o.topic = :topic AND o.partition = :partition")
    int deleteByTopicPartition(String topic, int partition);

    @Modifying
    @Query("DELETE FROM ConsumerOffset o WHERE o.expireTimestamp < :threshold")
    int deleteExpiredOffsets(Instant threshold);

    /**
     * Calculate consumer lag for a group/topic/partition
     */
    @Query(value = "SELECT p.log_end_offset - COALESCE(o.committed_offset, 0) AS lag " +
            "FROM partitions p " +
            "LEFT JOIN consumer_offsets o ON o.topic = (SELECT name FROM topics WHERE topic_id = p.topic_id) " +
            "AND o.partition = p.partition_number AND o.group_id = :groupId " +
            "WHERE p.topic_id = (SELECT topic_id FROM topics WHERE name = :topic) " +
            "AND p.partition_number = :partition", nativeQuery = true)
    Long calculateLag(String groupId, String topic, int partition);

    /**
     * Get total lag for a consumer group
     */
    @Query(value = "SELECT COALESCE(SUM(p.log_end_offset - COALESCE(o.committed_offset, 0)), 0) AS total_lag " +
            "FROM partitions p " +
            "JOIN topics t ON t.topic_id = p.topic_id " +
            "LEFT JOIN consumer_offsets o ON o.topic = t.name " +
            "AND o.partition = p.partition_number AND o.group_id = :groupId " +
            "WHERE t.name IN :topics", nativeQuery = true)
    Long calculateTotalLag(String groupId, List<String> topics);
}
