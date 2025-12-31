package com.kafka.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consumer Group Member entity representing a consumer instance.
 * 
 * Each member:
 * - Has a unique member ID assigned by coordinator
 * - Subscribes to topics
 * - Is assigned partitions by group leader
 * - Must heartbeat to stay in group
 */
@Entity
@Table(name = "consumer_group_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumerGroupMember {

    /**
     * Unique member ID assigned by coordinator.
     * Format: {clientId}-{uuid}
     */
    @Id
    @Column(name = "member_id")
    private String memberId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    /**
     * Client-provided identifier for the consumer.
     */
    @Column(name = "client_id")
    private String clientId;

    /**
     * IP address of the client.
     */
    @Column(name = "client_host")
    private String clientHost;

    /**
     * Session timeout - time to wait for heartbeat before removing member.
     */
    @Column(name = "session_timeout_ms")
    @Builder.Default
    private Integer sessionTimeoutMs = 45000;

    /**
     * Rebalance timeout - time to wait for member to rejoin during rebalance.
     */
    @Column(name = "rebalance_timeout_ms")
    @Builder.Default
    private Integer rebalanceTimeoutMs = 300000;

    /**
     * Expected interval between heartbeats.
     */
    @Column(name = "heartbeat_interval_ms")
    @Builder.Default
    private Integer heartbeatIntervalMs = 3000;

    /**
     * Topics this member has subscribed to.
     */
    @Column(name = "subscribed_topics", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> subscribedTopics;

    /**
     * Partitions assigned to this member.
     * JSON format: [{"topic": "orders", "partitions": [0, 1, 2]}]
     */
    @Column(name = "assigned_partitions", columnDefinition = "jsonb")
    private String assignedPartitions;

    /**
     * Member metadata for custom assignment strategies.
     */
    @Column(name = "metadata")
    private byte[] metadata;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
        lastHeartbeat = LocalDateTime.now();
    }

    /**
     * Update heartbeat timestamp.
     */
    public void heartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
    }

    /**
     * Check if member's session has expired.
     */
    public boolean isSessionExpired() {
        if (lastHeartbeat == null)
            return true;
        return LocalDateTime.now().isAfter(
                lastHeartbeat.plusNanos(sessionTimeoutMs * 1_000_000L));
    }

    /**
     * Check if this member is the group leader.
     */
    public boolean isLeader(ConsumerGroup group) {
        return memberId.equals(group.getLeaderMemberId());
    }
}
