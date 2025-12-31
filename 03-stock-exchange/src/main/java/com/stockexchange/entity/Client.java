package com.stockexchange.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Client entity representing a trading participant.
 */
@Entity
@Table(name = "clients", indexes = {
        @Index(name = "idx_clients_email", columnList = "email", unique = true),
        @Index(name = "idx_clients_api_key", columnList = "api_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "email", length = 255, unique = true, nullable = false)
    private String email;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", length = 20, nullable = false)
    private ClientType clientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private ClientStatus status = ClientStatus.ACTIVE;

    @Column(name = "api_key", length = 64, unique = true)
    private String apiKey;

    @Column(name = "api_secret", length = 128)
    private String apiSecret;

    @Column(name = "max_orders_per_second")
    @Builder.Default
    private Integer maxOrdersPerSecond = 100;

    @Column(name = "rate_limit_per_second")
    @Builder.Default
    private Integer rateLimitPerSecond = 100;

    @Column(name = "max_open_orders")
    @Builder.Default
    private Integer maxOpenOrders = 10000;

    @Column(name = "is_market_maker")
    @Builder.Default
    private Boolean isMarketMaker = false;

    @Column(name = "has_dma_access")
    @Builder.Default
    private Boolean hasDmaAccess = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public boolean canTrade() {
        return status == ClientStatus.ACTIVE;
    }

    public enum ClientType {
        RETAIL,
        INSTITUTIONAL,
        MARKET_MAKER,
        BROKER_DEALER,
        PROPRIETARY
    }

    public enum ClientStatus {
        PENDING,
        ACTIVE,
        SUSPENDED,
        CLOSED
    }
}
