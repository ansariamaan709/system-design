package com.stockexchange.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Trading account entity.
 */
@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_accounts_client", columnList = "client_id"),
        @Index(name = "idx_accounts_number", columnList = "account_number", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "account_number", length = 20, unique = true, nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20, nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "cash_balance", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal cashBalance = BigDecimal.ZERO;

    @Column(name = "buying_power", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal buyingPower = BigDecimal.ZERO;

    @Column(name = "margin_used", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal marginUsed = BigDecimal.ZERO;

    @Column(name = "margin_available", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal marginAvailable = BigDecimal.ZERO;

    @Column(name = "equity", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal equity = BigDecimal.ZERO;

    @Column(name = "portfolio_value", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal portfolioValue = BigDecimal.ZERO;

    @Column(name = "unrealized_pnl", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal unrealizedPnl = BigDecimal.ZERO;

    @Column(name = "realized_pnl", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(name = "realized_pnl_today", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal realizedPnlToday = BigDecimal.ZERO;

    @Column(name = "daily_loss_limit", precision = 20, scale = 4)
    private BigDecimal dailyLossLimit;

    @Column(name = "max_position_size")
    private Long maxPositionSize;

    @Column(name = "max_position_value", precision = 20, scale = 4)
    private BigDecimal maxPositionValue;

    @Column(name = "max_order_value", precision = 20, scale = 4)
    private BigDecimal maxOrderValue;

    @Column(name = "max_orders_per_second")
    @Builder.Default
    private Integer maxOrdersPerSecond = 100;

    @Column(name = "rate_limit_per_second")
    @Builder.Default
    private Integer rateLimitPerSecond = 100;

    @Column(name = "can_trade")
    @Builder.Default
    private Boolean canTrade = true;

    @Column(name = "margin_enabled")
    @Builder.Default
    private Boolean marginEnabled = false;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Check if account can place orders
     */
    public boolean isCanTrade() {
        return canTrade != null && canTrade && status == AccountStatus.ACTIVE;
    }

    /**
     * Check if margin is enabled
     */
    public boolean isMarginEnabled() {
        return marginEnabled != null && marginEnabled;
    }

    /**
     * Reserve buying power for an order
     */
    public void reserveBuyingPower(BigDecimal amount) {
        if (buyingPower.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient buying power");
        }
        buyingPower = buyingPower.subtract(amount);
    }

    /**
     * Release buying power (order cancelled/rejected)
     */
    public void releaseBuyingPower(BigDecimal amount) {
        buyingPower = buyingPower.add(amount);
    }

    /**
     * Update cash balance after trade execution
     */
    public void updateCashBalance(BigDecimal amount) {
        cashBalance = cashBalance.add(amount);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            buyingPower = buyingPower.add(amount);
        }
    }

    public enum AccountType {
        CASH,
        MARGIN,
        IRA,
        INSTITUTIONAL
    }

    public enum AccountStatus {
        PENDING,
        ACTIVE,
        SUSPENDED,
        RESTRICTED,
        CLOSED
    }
}
