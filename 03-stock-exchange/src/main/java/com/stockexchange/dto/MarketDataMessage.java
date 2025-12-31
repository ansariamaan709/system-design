package com.stockexchange.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * WebSocket message for real-time market data updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataMessage {

    private MessageType type;
    private String symbol;
    private long timestamp;
    private long sequence;
    private Object data;

    public enum MessageType {
        // Subscription management
        SUBSCRIBE,
        UNSUBSCRIBE,
        SUBSCRIBED,
        UNSUBSCRIBED,
        ERROR,

        // Market data
        QUOTE, // Best bid/ask update
        TRADE, // Trade tick
        DEPTH, // Order book depth update
        SNAPSHOT, // Full order book snapshot
        TICKER, // Summary ticker

        // Order updates (private)
        ORDER_NEW,
        ORDER_PARTIAL_FILL,
        ORDER_FILLED,
        ORDER_CANCELLED,
        ORDER_REJECTED,
        EXECUTION,

        // System
        HEARTBEAT,
        SYSTEM_STATUS
    }

    /**
     * Quote update (Level 1)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Quote {
        private String symbol;
        private BigDecimal bidPrice;
        private Long bidSize;
        private BigDecimal askPrice;
        private Long askSize;
        private BigDecimal spread;
        private long timestamp;
    }

    /**
     * Trade tick
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeTick {
        private String symbol;
        private long tradeId;
        private BigDecimal price;
        private Long quantity;
        private String side; // BUY or SELL (aggressor)
        private long timestamp;
    }

    /**
     * Order book depth update
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepthUpdate {
        private String symbol;
        private List<PriceLevel> bids;
        private List<PriceLevel> asks;
        private long timestamp;
    }

    /**
     * Price level for depth
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceLevel {
        private BigDecimal price;
        private Long quantity;
        private Integer orders;
    }

    /**
     * Summary ticker
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ticker {
        private String symbol;
        private BigDecimal lastPrice;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private Long volume;
        private BigDecimal change;
        private BigDecimal changePercent;
        private BigDecimal vwap;
        private long timestamp;
    }

    /**
     * Order update
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderUpdate {
        private Long orderId;
        private String clientOrderId;
        private String symbol;
        private String side;
        private String status;
        private Long quantity;
        private Long filledQuantity;
        private Long remainingQuantity;
        private BigDecimal price;
        private BigDecimal averagePrice;
        private String rejectReason;
        private long timestamp;
    }

    /**
     * Execution report
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Execution {
        private Long executionId;
        private Long orderId;
        private String symbol;
        private String side;
        private BigDecimal price;
        private Long quantity;
        private BigDecimal commission;
        private long timestamp;
    }

    // Factory methods
    public static MarketDataMessage quote(Quote quote) {
        return MarketDataMessage.builder()
                .type(MessageType.QUOTE)
                .symbol(quote.getSymbol())
                .timestamp(System.currentTimeMillis())
                .data(quote)
                .build();
    }

    public static MarketDataMessage trade(TradeTick trade) {
        return MarketDataMessage.builder()
                .type(MessageType.TRADE)
                .symbol(trade.getSymbol())
                .timestamp(System.currentTimeMillis())
                .data(trade)
                .build();
    }

    public static MarketDataMessage depth(DepthUpdate depth) {
        return MarketDataMessage.builder()
                .type(MessageType.DEPTH)
                .symbol(depth.getSymbol())
                .timestamp(System.currentTimeMillis())
                .data(depth)
                .build();
    }

    public static MarketDataMessage ticker(Ticker ticker) {
        return MarketDataMessage.builder()
                .type(MessageType.TICKER)
                .symbol(ticker.getSymbol())
                .timestamp(System.currentTimeMillis())
                .data(ticker)
                .build();
    }

    public static MarketDataMessage orderUpdate(OrderUpdate update) {
        return MarketDataMessage.builder()
                .type(MessageType.valueOf("ORDER_" + update.getStatus()))
                .symbol(update.getSymbol())
                .timestamp(System.currentTimeMillis())
                .data(update)
                .build();
    }

    public static MarketDataMessage heartbeat() {
        return MarketDataMessage.builder()
                .type(MessageType.HEARTBEAT)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static MarketDataMessage error(String message) {
        return MarketDataMessage.builder()
                .type(MessageType.ERROR)
                .timestamp(System.currentTimeMillis())
                .data(message)
                .build();
    }
}
