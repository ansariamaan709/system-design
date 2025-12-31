package com.stockexchange.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Trade entity representing an executed trade between two orders.
 * Immutable after creation for audit trail.
 */
@Entity
@Table(name = "trades", indexes = {
        @Index(name = "idx_trades_buy_order", columnList = "buy_order_id"),
        @Index(name = "idx_trades_sell_order", columnList = "sell_order_id"),
        @Index(name = "idx_trades_buyer", columnList = "buyer_id, executed_at DESC"),
        @Index(name = "idx_trades_seller", columnList = "seller_id, executed_at DESC"),
        @Index(name = "idx_trades_symbol", columnList = "symbol, executed_at DESC"),
        @Index(name = "idx_trades_settlement", columnList = "settlement_date, settlement_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trade_id", updatable = false, nullable = false)
    private Long tradeId;

    @Column(name = "execution_id", length = 64, unique = true)
    private String executionId;

    @Column(name = "buy_order_id", nullable = false)
    private Long buyOrderId;

    @Column(name = "sell_order_id", nullable = false)
    private Long sellOrderId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_account_id", nullable = false)
    private Long buyerAccountId;

    @Column(name = "seller_account_id", nullable = false)
    private Long sellerAccountId;

    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Column(name = "price", precision = 20, scale = 4, nullable = false)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "trade_value", precision = 20, scale = 4, nullable = false)
    private BigDecimal tradeValue;

    @Column(name = "value", precision = 20, scale = 4, nullable = false)
    private BigDecimal value;

    @Column(name = "buyer_commission", precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal buyerCommission = BigDecimal.ZERO;

    @Column(name = "seller_commission", precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal sellerCommission = BigDecimal.ZERO;

    @Column(name = "aggressor_side", length = 10)
    private String aggressorSide;

    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Column(name = "executed_at", updatable = false, nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", length = 20, nullable = false)
    @Builder.Default
    private SettlementStatus settlementStatus = SettlementStatus.PENDING;

    @Column(name = "settled_at")
    private Instant settledAt;

    @PrePersist
    public void prePersist() {
        if (tradeValue == null && price != null && quantity != null) {
            tradeValue = price.multiply(BigDecimal.valueOf(quantity));
        }
        if (value == null && price != null && quantity != null) {
            value = price.multiply(BigDecimal.valueOf(quantity));
        }
        if (settlementDate == null) {
            // T+2 settlement
            settlementDate = LocalDate.now().plusDays(2);
        }
        if (executionId == null) {
            executionId = "EX" + System.nanoTime();
        }
        if (executedAt == null) {
            executedAt = LocalDateTime.now();
        }
    }

    public BigDecimal getValue() {
        if (value != null)
            return value;
        if (tradeValue != null)
            return tradeValue;
        if (price != null && quantity != null) {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }

    public enum SettlementStatus {
        PENDING,
        MATCHED,
        AFFIRMED,
        SETTLED,
        FAILED,
        CANCELLED
    }
}
