package com.kafka.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Consumer Group entity for coordinating consumer instances.
 * 
 * Consumer groups enable:
 * - Load balancing: Partitions distributed among group members
 * - Fault tolerance: Automatic reassignment on member failure
 * - Offset management: Group-level offset tracking
 * 
 * Rebalance Protocol:
 * 1. Member joins/leaves → triggers rebalance
 * 2. All members send JoinGroup request
 * 3. Leader performs partition assignment
 * 4. All members send SyncGroup request
 * 5. Coordinator distributes assignments
 */
@Entity
@Table(name = "consumer_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumerGroup {

    @Id
    @Column(name = "group_id")
    private String groupId;

    /**
     * Group state machine:
     * EMPTY → PREPARING_REBALANCE → COMPLETING_REBALANCE → STABLE → EMPTY
     * ↓
     * DEAD
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private GroupState state = GroupState.EMPTY;

    @Column(name = "protocol_type")
    @Builder.Default
    private String protocolType = "consumer";

    private String protocol;

    @Column(name = "leader_member_id")
    private String leaderMemberId;

    /**
     * Generation ID increments on each rebalance.
     * Used to fence old members from committing offsets.
     */
    @Column(name = "generation_id")
    @Builder.Default
    private Integer generationId = 0;

    @Column(name = "coordinator_broker_id")
    private Integer coordinatorBrokerId;

    @Column(name = "assignment_strategy")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AssignmentStrategy assignmentStrategy = AssignmentStrategy.RANGE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum GroupState {
        /**
         * Group has no members.
         */
        EMPTY,

        /**
         * Group is waiting for all members to join.
         */
        PREPARING_REBALANCE,

        /**
         * Group is waiting for leader to send assignment.
         */
        COMPLETING_REBALANCE,

        /**
         * Group is stable with assigned partitions.
         */
        STABLE,

        /**
         * Group is being removed.
         */
        DEAD
    }

    public enum AssignmentStrategy {
        /**
         * Assign contiguous partition ranges to each consumer.
         * E.g., 10 partitions, 3 consumers: [0-3], [4-6], [7-9]
         */
        RANGE,

        /**
         * Assign partitions round-robin across consumers.
         * E.g., 10 partitions, 3 consumers: [0,3,6,9], [1,4,7], [2,5,8]
         */
        ROUND_ROBIN,

        /**
         * Minimize partition movement during rebalance.
         * Tries to keep existing assignments when possible.
         */
        STICKY,

        /**
         * Incremental cooperative rebalancing.
         * Only revokes partitions that need to move.
         */
        COOPERATIVE_STICKY
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Transition to preparing rebalance state.
     */
    public void prepareRebalance() {
        this.state = GroupState.PREPARING_REBALANCE;
    }

    /**
     * Transition to completing rebalance state.
     */
    public void completeRebalance() {
        this.state = GroupState.COMPLETING_REBALANCE;
    }

    /**
     * Transition to stable state and increment generation.
     */
    public void becomeStable() {
        this.state = GroupState.STABLE;
        this.generationId++;
    }

    /**
     * Transition to empty state.
     */
    public void becomeEmpty() {
        this.state = GroupState.EMPTY;
        this.leaderMemberId = null;
        this.protocol = null;
    }

    /**
     * Mark group as dead.
     */
    public void markDead() {
        this.state = GroupState.DEAD;
    }

    /**
     * Check if group is in rebalancing state.
     */
    public boolean isRebalancing() {
        return state == GroupState.PREPARING_REBALANCE ||
                state == GroupState.COMPLETING_REBALANCE;
    }
}
