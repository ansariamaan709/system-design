package com.kafka.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Transaction entity for exactly-once semantics support.
 * 
 * Transactions enable atomic writes across multiple partitions:
 * 1. Producer starts transaction
 * 2. Writes to multiple partitions
 * 3. Commits or aborts atomically
 * 
 * Consumers can choose isolation level:
 * - read_committed: Only see committed messages
 * - read_uncommitted: See all messages (default)
 * 
 * Transaction state machine:
 * EMPTY → ONGOING → PREPARE_COMMIT/PREPARE_ABORT →
 * COMPLETE_COMMIT/COMPLETE_ABORT
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @Column(name = "transactional_id")
    private String transactionalId;

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Column(name = "producer_epoch", nullable = false)
    private Short producerEpoch;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionState state = TransactionState.EMPTY;

    @Column(name = "timeout_ms")
    @Builder.Default
    private Integer timeoutMs = 60000;

    @Column(name = "transaction_start_time")
    private LocalDateTime transactionStartTime;

    @Column(name = "last_update_time")
    private LocalDateTime lastUpdateTime;

    /**
     * Partitions involved in this transaction.
     * JSON: [{"topic": "orders", "partitions": [0, 1]}]
     */
    @Column(name = "partitions_in_txn", columnDefinition = "jsonb")
    @Builder.Default
    private String partitionsInTxn = "[]";

    /**
     * Offsets to commit with this transaction.
     * JSON: {"group1": {"topic-0": 100, "topic-1": 200}}
     */
    @Column(name = "pending_offsets", columnDefinition = "jsonb")
    @Builder.Default
    private String pendingOffsets = "{}";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum TransactionState {
        /**
         * No active transaction.
         */
        EMPTY,

        /**
         * Transaction in progress.
         */
        ONGOING,

        /**
         * Preparing to commit (2PC phase 1).
         */
        PREPARE_COMMIT,

        /**
         * Preparing to abort (2PC phase 1).
         */
        PREPARE_ABORT,

        /**
         * Commit completed (2PC phase 2).
         */
        COMPLETE_COMMIT,

        /**
         * Abort completed (2PC phase 2).
         */
        COMPLETE_ABORT,

        /**
         * Transaction coordinator is dead.
         */
        DEAD
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUpdateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdateTime = LocalDateTime.now();
    }

    /**
     * Begin a new transaction.
     */
    public void begin() {
        this.state = TransactionState.ONGOING;
        this.transactionStartTime = LocalDateTime.now();
    }

    /**
     * Prepare to commit.
     */
    public void prepareCommit() {
        this.state = TransactionState.PREPARE_COMMIT;
    }

    /**
     * Prepare to abort.
     */
    public void prepareAbort() {
        this.state = TransactionState.PREPARE_ABORT;
    }

    /**
     * Complete commit.
     */
    public void completeCommit() {
        this.state = TransactionState.COMPLETE_COMMIT;
    }

    /**
     * Complete abort.
     */
    public void completeAbort() {
        this.state = TransactionState.COMPLETE_ABORT;
    }

    /**
     * Check if transaction has timed out.
     */
    public boolean isTimedOut() {
        if (transactionStartTime == null)
            return false;
        return LocalDateTime.now().isAfter(
                transactionStartTime.plusNanos(timeoutMs * 1_000_000L));
    }

    /**
     * Check if this is an active transaction.
     */
    public boolean isActive() {
        return state == TransactionState.ONGOING ||
                state == TransactionState.PREPARE_COMMIT ||
                state == TransactionState.PREPARE_ABORT;
    }
}
