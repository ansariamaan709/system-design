package com.stockexchange.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Instrument/Security entity representing a tradeable symbol.
 */
@Entity
@Table(name = "instruments", indexes = {
        @Index(name = "idx_instruments_symbol", columnList = "symbol", unique = true),
        @Index(name = "idx_instruments_status", columnList = "trading_status"),
        @Index(name = "idx_instruments_type", columnList = "instrument_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instrument_id")
    private Integer instrumentId;

    @Column(name = "symbol", length = 20, unique = true, nullable = false)
    private String symbol;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", length = 20, nullable = false)
    private InstrumentType instrumentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private InstrumentType type;

    @Column(name = "exchange", length = 20, nullable = false)
    @Builder.Default
    private String exchange = "NYSE";

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "lot_size", nullable = false)
    @Builder.Default
    private Integer lotSize = 1;

    @Column(name = "tick_size", precision = 10, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal tickSize = new BigDecimal("0.01");

    @Column(name = "min_price", precision = 20, scale = 4)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 20, scale = 4)
    private BigDecimal maxPrice;

    @Column(name = "reference_price", precision = 20, scale = 4)
    private BigDecimal referencePrice;

    @Column(name = "last_price", precision = 20, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "prev_close", precision = 20, scale = 4)
    private BigDecimal prevClose;

    @Column(name = "previous_close", precision = 20, scale = 4)
    private BigDecimal previousClose;

    @Column(name = "open_price", precision = 20, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "open", precision = 20, scale = 4)
    private BigDecimal open;

    @Column(name = "high_price", precision = 20, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "high", precision = 20, scale = 4)
    private BigDecimal high;

    @Column(name = "low_price", precision = 20, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "low", precision = 20, scale = 4)
    private BigDecimal low;

    @Column(name = "volume_today")
    @Builder.Default
    private Long volumeToday = 0L;

    @Column(name = "volume")
    @Builder.Default
    private Long volume = 0L;

    @Column(name = "value_traded_today", precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal valueTradedToday = BigDecimal.ZERO;

    @Column(name = "value_traded", precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal valueTraded = BigDecimal.ZERO;

    @Column(name = "trade_count_today")
    @Builder.Default
    private Integer tradeCountToday = 0;

    @Column(name = "last_trade_time")
    private LocalDateTime lastTradeTime;

    @Column(name = "max_order_quantity")
    @Builder.Default
    private Long maxOrderQuantity = 1000000L;

    @Column(name = "circuit_breaker_up_pct", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal circuitBreakerUpPct = new BigDecimal("10.00");

    @Column(name = "circuit_breaker_down_pct", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal circuitBreakerDownPct = new BigDecimal("10.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "trading_status", length = 20, nullable = false)
    @Builder.Default
    private TradingStatus tradingStatus = TradingStatus.OPEN;

    @Column(name = "tradable")
    @Builder.Default
    private Boolean tradable = true;

    @Column(name = "halt_reason", length = 255)
    private String haltReason;

    @Column(name = "halted_at")
    private Instant haltedAt;

    @Column(name = "ipo_date")
    private LocalDate ipoDate;

    @Column(name = "marginable")
    @Builder.Default
    private Boolean marginable = true;

    @Column(name = "shortable")
    @Builder.Default
    private Boolean shortable = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Check if trading is allowed
     */
    public boolean isTradeable() {
        return (tradable == null || tradable) &&
                (tradingStatus == TradingStatus.OPEN || tradingStatus == TradingStatus.PRE_MARKET
                        || tradingStatus == TradingStatus.AFTER_HOURS);
    }

    /**
     * Halt trading
     */
    public void halt(String reason) {
        tradingStatus = TradingStatus.HALTED;
        haltReason = reason;
        haltedAt = Instant.now();
    }

    /**
     * Resume trading
     */
    public void resume() {
        tradingStatus = TradingStatus.OPEN;
        haltReason = null;
        haltedAt = null;
    }

    /**
     * Update trade stats
     */
    public void updateTradeStats(BigDecimal price, long quantity) {
        lastPrice = price;
        volumeToday += quantity;
        volume = volumeToday;
        BigDecimal tradeVal = price.multiply(BigDecimal.valueOf(quantity));
        valueTradedToday = valueTradedToday.add(tradeVal);
        valueTraded = valueTradedToday;
        tradeCountToday++;
        lastTradeTime = java.time.LocalDateTime.now();

        if (openPrice == null) {
            openPrice = price;
            open = price;
        }
        if (highPrice == null || price.compareTo(highPrice) > 0) {
            highPrice = price;
            high = price;
        }
        if (lowPrice == null || price.compareTo(lowPrice) < 0) {
            lowPrice = price;
            low = price;
        }
    }

    /**
     * Reset daily stats (called at market open)
     */
    public void resetDailyStats() {
        prevClose = lastPrice;
        previousClose = lastPrice;
        openPrice = null;
        open = null;
        highPrice = null;
        high = null;
        lowPrice = null;
        low = null;
        volumeToday = 0L;
        volume = 0L;
        valueTradedToday = BigDecimal.ZERO;
        valueTraded = BigDecimal.ZERO;
        tradeCountToday = 0;
    }

    @PrePersist
    @PreUpdate
    public void syncFields() {
        if (instrumentType != null && type == null) {
            type = instrumentType;
        } else if (type != null && instrumentType == null) {
            instrumentType = type;
        }
    }

    /**
     * Validate price against tick size
     */
    public boolean isValidPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        // Check if price is a multiple of tick size
        return price.remainder(tickSize).compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Round price to valid tick size
     */
    public BigDecimal roundToTick(BigDecimal price) {
        return price.divide(tickSize, 0, java.math.RoundingMode.HALF_UP).multiply(tickSize);
    }

    public enum InstrumentType {
        STOCK,
        ETF,
        OPTION,
        FUTURE,
        FOREX,
        CRYPTO,
        BOND,
        INDEX
    }

    public enum TradingStatus {
        PRE_MARKET,
        OPEN,
        HALTED,
        AUCTION,
        CLOSED,
        AFTER_HOURS,
        SUSPENDED
    }
}
