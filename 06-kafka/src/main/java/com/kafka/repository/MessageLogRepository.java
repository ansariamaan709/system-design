package com.kafka.repository;

import com.kafka.entity.MessageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for database-backed message storage.
 * Used for small deployments or when file-based storage is not suitable.
 */
@Repository
public interface MessageLogRepository extends JpaRepository<MessageLog, UUID> {

    @Query("SELECT m FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition " +
            "AND m.offset = :offset")
    Optional<MessageLog> findByTopicPartitionOffset(String topic, int partition, long offset);

    @Query("SELECT m FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition " +
            "AND m.offset >= :startOffset AND m.offset < :endOffset ORDER BY m.offset")
    List<MessageLog> fetchMessages(String topic, int partition, long startOffset, long endOffset);

    @Query("SELECT m FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition " +
            "AND m.offset >= :startOffset ORDER BY m.offset")
    List<MessageLog> fetchMessagesFrom(String topic, int partition, long startOffset);

    @Query(value = "SELECT * FROM message_log WHERE topic = :topic AND partition = :partition " +
            "AND offset >= :startOffset ORDER BY offset LIMIT :limit", nativeQuery = true)
    List<MessageLog> fetchMessagesWithLimit(String topic, int partition, long startOffset, int limit);

    @Query("SELECT MAX(m.offset) FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition")
    Long findMaxOffset(String topic, int partition);

    @Query("SELECT MIN(m.offset) FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition")
    Long findMinOffset(String topic, int partition);

    @Query("SELECT m FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition " +
            "AND m.timestamp >= :timestamp ORDER BY m.offset LIMIT 1")
    Optional<MessageLog> findByTimestamp(String topic, int partition, Instant timestamp);

    @Query("SELECT COUNT(m) FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition")
    long countMessages(String topic, int partition);

    @Query("SELECT COALESCE(SUM(LENGTH(m.value)), 0) FROM MessageLog m " +
            "WHERE m.topic = :topic AND m.partition = :partition")
    long calculateSize(String topic, int partition);

    // Retention operations
    @Modifying
    @Query("DELETE FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition " +
            "AND m.offset < :offset")
    int deleteByOffset(String topic, int partition, long offset);

    @Modifying
    @Query("DELETE FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition " +
            "AND m.timestamp < :threshold")
    int deleteByTimestamp(String topic, int partition, Instant threshold);

    @Modifying
    @Query("DELETE FROM MessageLog m WHERE m.topic = :topic")
    int deleteByTopic(String topic);

    // Compaction support
    @Query(value = "SELECT DISTINCT ON (key) * FROM message_log " +
            "WHERE topic = :topic AND partition = :partition AND key IS NOT NULL " +
            "ORDER BY key, offset DESC", nativeQuery = true)
    List<MessageLog> findLatestByKey(String topic, int partition);

    @Modifying
    @Query(value = "DELETE FROM message_log m1 " +
            "WHERE topic = :topic AND partition = :partition AND key IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM message_log m2 " +
            "WHERE m2.topic = m1.topic AND m2.partition = m1.partition " +
            "AND m2.key = m1.key AND m2.offset > m1.offset)", nativeQuery = true)
    int compactByKey(String topic, int partition);

    // Transaction support
    @Query("SELECT m FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition " +
            "AND m.producerId = :producerId AND m.producerEpoch = :epoch " +
            "ORDER BY m.sequence")
    List<MessageLog> findByProducerIdAndEpoch(String topic, int partition, Long producerId, Short epoch);

    @Modifying
    @Query("DELETE FROM MessageLog m WHERE m.topic = :topic AND m.partition = :partition " +
            "AND m.producerId = :producerId AND m.producerEpoch = :epoch AND m.isTransactional = true " +
            "AND m.transactionState = 'ABORTED'")
    int deleteAbortedTransactionalMessages(String topic, int partition, Long producerId, Short epoch);
}
