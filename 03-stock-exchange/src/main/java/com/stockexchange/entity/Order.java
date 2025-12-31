package com.stockexchange.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order entity representing a trading order.
 * Optimized for fast access with minimal object allocation.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_client", columnList = "client_id, created_at DESC"),
        @Index(name = "idx_orders_symbol", columnList = "symbol, status"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_client_order_id", columnList = "client_id, client_order_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @Column(name = "order_id", updatable = false, nullable = false)
    private Long orderId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "client_order_id", length = 64, nullable = false)
    private String clientOrderId;

    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 4, nullable = false)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 15, nullable = false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_in_force", length = 10, nullable = false)
    @Builder.Default
    private TimeInForce timeInForce = TimeInForce.DAY;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.NEW;

    @Column(name = "price", precision = 20, scale = 4)
    private BigDecimal price;

    @Column(name = "stop_price", precision = 20, scale = 4)
    private BigDecimal stopPrice;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "filled_quantity", nullable = false)
    @Builder.Default
    private Long filledQuantity = 0L;

    @Column(name = "remaining_quantity", nullable = false)
    private Long remainingQuantity;

    @Column(name = "avg_fill_price", precision = 20, scale = 4)
    private BigDecimal avgFillPrice;

    @Column(name = "commission", precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal commission = BigDecimal.ZERO;

    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "expire_time")
    private Instant expireTime;

    @Column(name = "display_quantity")
    private Long displayQuantity;

    @Column(name = "fill_count")
    @Builder.Default
    private Integer fillCount = 0;

    // Transient fields for engine processing
    @Transient
    private Long submitTime;

    @Transient
    private Long matchTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "filled_at")
    private Instant filledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    public void prePersist() {
        if (remainingQuantity == null) {
            remainingQuantity = quantity;
        }
    }

    /**
     * Check if order is fully filled
     */
    public boolean isFilled() {
        return filledQuantity >= quantity;
    }

    /**
     * Check if order can be cancelled
     */
    public boolean isCancellable() {
        return status == OrderStatus.NEW ||
                status == OrderStatus.PARTIALLY_FILLED ||
                status == OrderStatus.PENDING_NEW;
    }

    /**
     * Check if order is active (can match)
     */
    public boolean isActive() {
        return status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED;
    }

    /**
     * Get remaining quantity to fill
     */
    public long getRemaining() {
        return quantity - filledQuantity;
    }

    /**
     * Fill the order with given quantity and price
     */
    public void fill(long fillQuantity, BigDecimal fillPrice) {
        if (fillQuantity <= 0 || fillQuantity > getRemaining()) {
            throw new IllegalArgumentException("Invalid fill quantity: " + fillQuantity);
        }

        // Calculate new average fill price
        if (avgFillPrice == null || filledQuantity == 0) {
            avgFillPrice = fillPrice;
        } else {
            BigDecimal totalValue = avgFillPrice.multiply(BigDecimal.valueOf(filledQuantity))
                    .add(fillPrice.multiply(BigDecimal.valueOf(fillQuantity)));
            long newFilledQty = filledQuantity + fillQuantity;
            avgFillPrice = totalValue.divide(BigDecimal.valueOf(newFilledQty), 4, java.math.RoundingMode.HALF_UP);
        }

        filledQuantity += fillQuantity;
        remainingQuantity = quantity - filledQuantity;

        if (isFilled()) {
            status = OrderStatus.FILLED;
            filledAt = Instant.now();
        } else {
            status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    /**
     * Cancel the order
     */
    public void cancel(String reason) {
        if (!isCancellable()) {
            throw new IllegalStateException("Order cannot be cancelled in status: " + status);
        }
        status = OrderStatus.CANCELLED;
        cancelledAt = Instant.now();
        rejectReason = reason;
    }

    /**
     * Reject the order
     */
    public void reject(String reason) {
        status = OrderStatus.REJECTED;
        rejectReason = reason;
    }

    public enum Side {
        BUY,
        SELL
    }

    public enum OrderType {
        MARKET,
        LIMIT,
        STOP,
        STOP_LIMIT,
        MARKET_ON_CLOSE,
        LIMIT_ON_CLOSE
    }

    public enum TimeInForce {
        DAY, // Good for day
        GTC, // Good till cancelled
        IOC, // Immediate or cancel
        FOK, // Fill or kill
        GTD, // Good till date
        OPG, // At the opening
        CLO // At the close
    }

    public enum OrderStatus {
        PENDING_NEW,
        NEW,
        PARTIALLY_FILLED,
        FILLED,
        CANCELLED,
        PENDING_CANCEL,
        REJECTED,
        EXPIRED,
        SUSPENDED
    }
}
