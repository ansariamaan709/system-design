package com.stockexchange.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response DTO for market data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataResponse {

    private String symbol;
    private Instant timestamp;

    // Level 1 (Top of Book)
    private BigDecimal bestBid;
    private Long bestBidSize;
    private BigDecimal bestAsk;
    private Long bestAskSize;
    private BigDecimal spread;

    // Last trade
    private BigDecimal lastPrice;
    private Long lastSize;
    private String lastSide;

    // OHLCV (Daily)
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;
    private BigDecimal vwap;
    private BigDecimal valueTraded;

    // Change
    private BigDecimal change;
    private BigDecimal changePercent;

    // Level 2 (Depth of Book)
    private List<PriceLevel> bids;
    private List<PriceLevel> asks;

    // Trading session
    private String tradingStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceLevel {
        private BigDecimal price;
        private Long quantity;
        private Integer orderCount;
    }
}
