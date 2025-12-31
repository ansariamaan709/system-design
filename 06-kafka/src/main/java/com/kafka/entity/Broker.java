package com.kafka.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Broker entity representing a Kafka broker in the cluster.
 * 
 * Brokers are the core servers that:
 * - Store topic partitions
 * - Handle produce/fetch requests
 * - Participate in leader election
 * - Replicate data for fault tolerance
 */
@Entity
@Table(name = "brokers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Broker {

    @Id
    @Column(name = "broker_id")
    private Integer brokerId;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port;

    private String rack;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BrokerStatus status = BrokerStatus.ONLINE;

    @Column(columnDefinition = "jsonb")
    private String endpoints;

    private String version;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum BrokerStatus {
        ONLINE,
        OFFLINE,
        STARTING,
        SHUTTING_DOWN,
        MAINTENANCE
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastHeartbeat = LocalDateTime.now();
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    public String getEndpoint() {
        return host + ":" + port;
    }

    public boolean isOnline() {
        return status == BrokerStatus.ONLINE;
    }

    public void heartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
    }
}
