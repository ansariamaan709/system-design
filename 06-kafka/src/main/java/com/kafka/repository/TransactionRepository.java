package com.kafka.repository;

import com.kafka.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByTransactionalId(String transactionalId);

    @Query("SELECT t FROM Transaction t WHERE t.transactionalId = :transactionalId AND t.state NOT IN " +
            "('COMPLETE_COMMIT', 'COMPLETE_ABORT', 'DEAD')")
    Optional<Transaction> findActiveTransaction(String transactionalId);

    @Query("SELECT t FROM Transaction t WHERE t.producerId = :producerId ORDER BY t.createdAt DESC")
    List<Transaction> findByProducerId(Long producerId);

    List<Transaction> findByState(Transaction.TransactionState state);

    @Query("SELECT t FROM Transaction t WHERE t.coordinatorBrokerId = :brokerId")
    List<Transaction> findByCoordinator(Integer brokerId);

    @Query("SELECT t FROM Transaction t WHERE t.state = 'ONGOING' AND t.lastUpdateTime < :threshold")
    List<Transaction> findTimedOutTransactions(Instant threshold);

    @Query("SELECT t FROM Transaction t WHERE t.state IN ('PREPARE_COMMIT', 'PREPARE_ABORT') " +
            "AND t.lastUpdateTime < :threshold")
    List<Transaction> findPendingTransactions(Instant threshold);

    @Modifying
    @Query("UPDATE Transaction t SET t.state = :state, t.lastUpdateTime = :timestamp " +
            "WHERE t.transactionalId = :transactionalId AND t.producerEpoch = :epoch")
    int updateState(String transactionalId, Short epoch, Transaction.TransactionState state, Instant timestamp);

    @Modifying
    @Query("UPDATE Transaction t SET t.state = 'DEAD' WHERE t.transactionalId = :transactionalId " +
            "AND t.producerEpoch < :epoch")
    int fenceOldEpochs(String transactionalId, Short epoch);

    @Modifying
    @Query("DELETE FROM Transaction t WHERE t.state IN ('COMPLETE_COMMIT', 'COMPLETE_ABORT') " +
            "AND t.lastUpdateTime < :threshold")
    int deleteCompletedTransactions(Instant threshold);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.state = 'ONGOING'")
    long countOngoingTransactions();

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.coordinatorBrokerId = :brokerId AND t.state = 'ONGOING'")
    int countOngoingByCoordinator(Integer brokerId);
}
