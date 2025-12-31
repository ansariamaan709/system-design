package com.stockexchange.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Position entity representing holdings in a specific instrument.
 */
@Entity
@Table(name = "positions", indexes = {
        @Index(name = "idx_positions_account", columnList = "account_id"),
        @Index(name = "idx_positions_symbol", columnList = "symbol")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_symbol", columnNames = { "account_id", "symbol" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Long quantity = 0L;

    @Column(name = "avg_cost", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Column(name = "average_cost", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal averageCost = BigDecimal.ZERO;

    @Column(name = "current_price", precision = 20, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "market_price", precision = 20, scale = 4)
    private BigDecimal marketPrice;

    @Column(name = "market_value", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal marketValue = BigDecimal.ZERO;

    @Column(name = "cost_basis", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal costBasis = BigDecimal.ZERO;

    @Column(name = "unrealized_pnl", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal unrealizedPnl = BigDecimal.ZERO;

    @Column(name = "unrealized_pnl_pct", precision = 10, scale = 4)
    private BigDecimal unrealizedPnlPct;

    @Column(name = "realized_pnl", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(name = "realized_pnl_today", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal realizedPnlToday = BigDecimal.ZERO;

    @Column(name = "today_pnl", precision = 20, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal todayPnl = BigDecimal.ZERO;

    @Column(name = "previous_close", precision = 20, scale = 4)
    private BigDecimal previousClose;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_side", length = 5)
    private PositionSide positionSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 5)
    private PositionSide side;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Add to position (buy)
     */
    public void addQuantity(long qty, BigDecimal price) {
        if (qty <= 0)
            return;

        // Calculate new average cost
        BigDecimal totalCost = avgCost.multiply(BigDecimal.valueOf(quantity))
                .add(price.multiply(BigDecimal.valueOf(qty)));
        long newQuantity = quantity + qty;

        if (newQuantity > 0) {
            avgCost = totalCost.divide(BigDecimal.valueOf(newQuantity), 4, java.math.RoundingMode.HALF_UP);
            averageCost = avgCost;
        }

        quantity = newQuantity;
        costBasis = avgCost.multiply(BigDecimal.valueOf(quantity));
        updatePositionSide();
        recalculateUnrealizedPnl();
    }

    /**
     * Remove from position (sell)
     */
    public BigDecimal removeQuantity(long qty, BigDecimal price) {
        if (qty <= 0 || qty > Math.abs(quantity)) {
            throw new IllegalArgumentException("Invalid quantity to remove: " + qty);
        }

        // Calculate realized P&L
        BigDecimal pnl;
        if (quantity > 0) {
            // Long position - selling
            pnl = price.subtract(avgCost).multiply(BigDecimal.valueOf(qty));
        } else {
            // Short position - covering
            pnl = avgCost.subtract(price).multiply(BigDecimal.valueOf(qty));
        }

        realizedPnl = realizedPnl.add(pnl);
        realizedPnlToday = realizedPnlToday.add(pnl);

        // Update quantity
        if (quantity > 0) {
            quantity -= qty;
        } else {
            quantity += qty;
        }

        costBasis = avgCost.multiply(BigDecimal.valueOf(Math.abs(quantity)));
        updatePositionSide();
        recalculateUnrealizedPnl();

        return pnl;
    }

    /**
     * Update market value with current price
     */
    public void updateMarketPrice(BigDecimal price) {
        this.currentPrice = price;
        this.marketPrice = price;
        recalculateUnrealizedPnl();
    }

    /**
     * Recalculate unrealized P&L
     */
    public void recalculateUnrealizedPnl() {
        if (currentPrice == null || quantity == 0) {
            unrealizedPnl = BigDecimal.ZERO;
            unrealizedPnlPct = BigDecimal.ZERO;
            marketValue = BigDecimal.ZERO;
            return;
        }

        marketValue = currentPrice.multiply(BigDecimal.valueOf(Math.abs(quantity)));

        if (quantity > 0) {
            // Long position
            unrealizedPnl = currentPrice.subtract(avgCost).multiply(BigDecimal.valueOf(quantity));
        } else {
            // Short position
            unrealizedPnl = avgCost.subtract(currentPrice).multiply(BigDecimal.valueOf(Math.abs(quantity)));
        }

        if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
            unrealizedPnlPct = unrealizedPnl.divide(costBasis, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
    }

    private void updatePositionSide() {
        if (quantity > 0) {
            positionSide = PositionSide.LONG;
            side = PositionSide.LONG;
        } else if (quantity < 0) {
            positionSide = PositionSide.SHORT;
            side = PositionSide.SHORT;
        } else {
            positionSide = PositionSide.FLAT;
            side = PositionSide.FLAT;
        }
    }

    public enum PositionSide {
        LONG,
        SHORT,
        FLAT
    }
}
