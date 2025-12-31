package com.kafka.repository;

import com.kafka.entity.ProducerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProducerIdRepository extends JpaRepository<ProducerId, UUID> {

    Optional<ProducerId> findByProducerId(Long producerId);

    @Query("SELECT p FROM ProducerId p WHERE p.transactionalId = :transactionalId")
    Optional<ProducerId> findByTransactionalId(String transactionalId);

    @Query("SELECT p FROM ProducerId p WHERE p.coordinatorBrokerId = :brokerId")
    List<ProducerId> findByCoordinator(Integer brokerId);

    @Query("SELECT MAX(p.producerId) FROM ProducerId p")
    Long findMaxProducerId();

    @Modifying
    @Query("UPDATE ProducerId p SET p.producerEpoch = p.producerEpoch + 1 WHERE p.producerId = :producerId")
    int incrementEpoch(Long producerId);

    @Modifying
    @Query("UPDATE ProducerId p SET p.lastSequenceNumber = :seq WHERE p.producerId = :producerId")
    int updateSequence(Long producerId, Integer seq);

    @Modifying
    @Query("UPDATE ProducerId p SET p.currentTxnFirstOffset = :offset WHERE p.producerId = :producerId")
    int updateTxnFirstOffset(Long producerId, Long offset);

    @Query("SELECT p FROM ProducerId p WHERE p.lastUpdateTime < :threshold AND p.currentTxnFirstOffset IS NOT NULL")
    List<ProducerId> findHangingTransactions(LocalDateTime threshold);

    @Modifying
    @Query("DELETE FROM ProducerId p WHERE p.lastUpdateTime < :threshold AND p.currentTxnFirstOffset IS NULL")
    int deleteExpiredProducers(LocalDateTime threshold);

    @Query("SELECT COUNT(p) FROM ProducerId p WHERE p.coordinatorBrokerId = :brokerId")
    int countByCoordinator(Integer brokerId);
}
