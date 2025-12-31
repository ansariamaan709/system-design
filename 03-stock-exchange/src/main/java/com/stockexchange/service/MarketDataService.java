package com.stockexchange.service;

import com.stockexchange.dto.MarketDataMessage;
import com.stockexchange.dto.MarketDataResponse;
import com.stockexchange.engine.MatchingEngine;
import com.stockexchange.engine.OrderBook;
import com.stockexchange.entity.Instrument;
import com.stockexchange.repository.InstrumentRepository;
import com.stockexchange.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Market Data Service.
 * Provides real-time market data via WebSocket and REST.
 * Uses Redis for caching and cross-instance consistency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final MatchingEngine matchingEngine;
    private final InstrumentRepository instrumentRepository;
    private final TradeRepository tradeRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    // In-memory cache for ultra-fast access
    private final Map<String, MarketDataSnapshot> snapshotCache = new ConcurrentHashMap<>();

    private static final String LAST_PRICE_KEY_PREFIX = "market:last:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // ==================== Real-time Market Data ====================

    /**
     * Get Level 1 quote (best bid/ask).
     */
    public MarketDataResponse getQuote(String symbol) {
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);

        if (orderBook == null && instrument == null) {
            return null;
        }

        return buildMarketDataResponse(symbol, orderBook, instrument, 0);
    }

    /**
     * Get Level 2 order book depth.
     */
    public MarketDataResponse getDepth(String symbol, int levels) {
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);

        if (orderBook == null && instrument == null) {
            return null;
        }

        return buildMarketDataResponse(symbol, orderBook, instrument, levels);
    }

    /**
     * Get last trade price.
     */
    public BigDecimal getLastPrice(String symbol) {
        // Check memory cache first
        MarketDataSnapshot snapshot = snapshotCache.get(symbol);
        if (snapshot != null && snapshot.lastPrice != null) {
            return snapshot.lastPrice;
        }

        // Check Redis
        Object cached = redisTemplate.opsForValue().get(LAST_PRICE_KEY_PREFIX + symbol);
        if (cached != null) {
            return new BigDecimal(cached.toString());
        }

        // Check instrument
        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);
        if (instrument != null && instrument.getLastPrice() != null) {
            return instrument.getLastPrice();
        }

        // Check order book
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        if (orderBook != null) {
            return orderBook.getLastTradePrice();
        }

        return null;
    }

    /**
     * Get ticker summary for symbol.
     */
    public MarketDataMessage.Ticker getTicker(String symbol) {
        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        MarketDataSnapshot snapshot = snapshotCache.get(symbol);

        if (instrument == null) {
            return null;
        }

        BigDecimal lastPrice = instrument.getLastPrice();
        if (lastPrice == null && orderBook != null) {
            lastPrice = orderBook.getLastTradePrice();
        }
        if (lastPrice == null && snapshot != null) {
            lastPrice = snapshot.lastPrice;
        }

        // These fields are not available on Instrument in this project; derive
        // best-effort values.
        BigDecimal open = null;
        BigDecimal high = null;
        BigDecimal low = null;
        BigDecimal prevClose = null;
        Long volume = snapshot != null ? snapshot.volume : null;

        BigDecimal change = null;
        BigDecimal changePercent = null;

        return MarketDataMessage.Ticker.builder()
                .symbol(symbol)
                .lastPrice(lastPrice)
                .open(open)
                .high(high)
                .low(low)
                .close(prevClose)
                .volume(volume)
                .change(change)
                .changePercent(changePercent)
                .vwap(calculateVWAP(symbol))
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Get tickers for multiple symbols.
     */
    public List<MarketDataMessage.Ticker> getTickers(List<String> symbols) {
        return symbols.stream()
                .map(this::getTicker)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get all tickers.
     */
    public List<MarketDataMessage.Ticker> getAllTickers() {
        return instrumentRepository.findTradableInstruments()
                .stream()
                .map(i -> getTicker(i.getSymbol()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== Market Data Publishing ====================

    /**
     * Publish quote update (called on order book changes).
     */
    public void publishQuoteUpdate(String symbol) {
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        if (orderBook == null)
            return;

        MarketDataMessage.Quote quote = MarketDataMessage.Quote.builder()
                .symbol(symbol)
                .bidPrice(orderBook.getBestBid())
                .bidSize(orderBook.getBidDepth(1))
                .askPrice(orderBook.getBestAsk())
                .askSize(orderBook.getAskDepth(1))
                .spread(orderBook.getSpread())
                .timestamp(System.currentTimeMillis())
                .build();

        // Update cache
        updateSnapshot(symbol, quote);

        // Broadcast via WebSocket
        messagingTemplate.convertAndSend("/topic/quotes/" + symbol, MarketDataMessage.quote(quote));
    }

    /**
     * Publish trade tick (called on trade execution).
     */
    public void publishTrade(String symbol, long tradeId, BigDecimal price,
            long quantity, String side) {
        MarketDataMessage.TradeTick trade = MarketDataMessage.TradeTick.builder()
                .symbol(symbol)
                .tradeId(tradeId)
                .price(price)
                .quantity(quantity)
                .side(side)
                .timestamp(System.currentTimeMillis())
                .build();

        // Update cache
        MarketDataSnapshot snapshot = snapshotCache.computeIfAbsent(symbol,
                k -> new MarketDataSnapshot());
        snapshot.lastPrice = price;
        snapshot.lastQuantity = quantity;
        snapshot.volume = (snapshot.volume == null ? 0L : snapshot.volume) + quantity;

        // Cache in Redis
        redisTemplate.opsForValue().set(LAST_PRICE_KEY_PREFIX + symbol,
                price.toString(), CACHE_TTL);

        // Broadcast via WebSocket
        messagingTemplate.convertAndSend("/topic/trades/" + symbol, MarketDataMessage.trade(trade));
    }

    /**
     * Publish depth update.
     */
    public void publishDepthUpdate(String symbol, int levels) {
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        if (orderBook == null)
            return;

        List<MarketDataMessage.PriceLevel> bids = orderBook.getBids(levels).stream()
                .map(l -> MarketDataMessage.PriceLevel.builder()
                        .price(l.getPrice())
                        .quantity(l.getQuantity())
                        .orders(l.getOrderCount())
                        .build())
                .collect(Collectors.toList());

        List<MarketDataMessage.PriceLevel> asks = orderBook.getAsks(levels).stream()
                .map(l -> MarketDataMessage.PriceLevel.builder()
                        .price(l.getPrice())
                        .quantity(l.getQuantity())
                        .orders(l.getOrderCount())
                        .build())
                .collect(Collectors.toList());

        MarketDataMessage.DepthUpdate depth = MarketDataMessage.DepthUpdate.builder()
                .symbol(symbol)
                .bids(bids)
                .asks(asks)
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSend("/topic/depth/" + symbol, MarketDataMessage.depth(depth));
    }

    // ==================== Scheduled Tasks ====================

    /**
     * Publish ticker updates every second.
     */
    @Scheduled(fixedRate = 1000)
    public void publishTickerUpdates() {
        for (String symbol : snapshotCache.keySet()) {
            MarketDataMessage.Ticker ticker = getTicker(symbol);
            if (ticker != null) {
                messagingTemplate.convertAndSend("/topic/ticker/" + symbol,
                        MarketDataMessage.ticker(ticker));
            }
        }
    }

    /**
     * Send heartbeat every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        messagingTemplate.convertAndSend("/topic/system", MarketDataMessage.heartbeat());
    }

    /**
     * Clean stale cache entries.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanCache() {
        long now = System.currentTimeMillis();
        snapshotCache.entrySet().removeIf(e -> now - e.getValue().lastUpdate > Duration.ofMinutes(10).toMillis());
    }

    // ==================== Helper Methods ====================

    private MarketDataResponse buildMarketDataResponse(String symbol, OrderBook orderBook,
            Instrument instrument, int depthLevels) {
        MarketDataResponse.MarketDataResponseBuilder builder = MarketDataResponse.builder()
                .symbol(symbol)
                .timestamp(Instant.now());

        MarketDataSnapshot snapshot = snapshotCache.get(symbol);

        if (orderBook != null) {
            builder.bestBid(orderBook.getBestBid())
                    .bestBidSize(orderBook.getBidDepth(1))
                    .bestAsk(orderBook.getBestAsk())
                    .bestAskSize(orderBook.getAskDepth(1))
                    .spread(orderBook.getSpread())
                    .lastPrice(orderBook.getLastTradePrice())
                    .lastSize(orderBook.getLastTradeQuantity());

            if (depthLevels > 0) {
                builder.bids(orderBook.getBids(depthLevels).stream()
                        .map(l -> MarketDataResponse.PriceLevel.builder()
                                .price(l.getPrice())
                                .quantity(l.getQuantity())
                                .orderCount(l.getOrderCount())
                                .build())
                        .collect(Collectors.toList()));

                builder.asks(orderBook.getAsks(depthLevels).stream()
                        .map(l -> MarketDataResponse.PriceLevel.builder()
                                .price(l.getPrice())
                                .quantity(l.getQuantity())
                                .orderCount(l.getOrderCount())
                                .build())
                        .collect(Collectors.toList()));
            }
        } else if (snapshot != null) {
            builder.bestBid(snapshot.bidPrice)
                    .bestBidSize(snapshot.bidSize)
                    .bestAsk(snapshot.askPrice)
                    .bestAskSize(snapshot.askSize)
                    .lastPrice(snapshot.lastPrice)
                    .lastSize(snapshot.lastQuantity);

            if (snapshot.bidPrice != null && snapshot.askPrice != null) {
                builder.spread(snapshot.askPrice.subtract(snapshot.bidPrice));
            }
        }

        if (instrument != null) {
            builder.tradingStatus(instrument.getTradingStatus() != null ? instrument.getTradingStatus().name() : null);

            // If we still don't have a last price, fall back to Instrument's last price.
            if (snapshot == null && orderBook == null) {
                builder.lastPrice(instrument.getLastPrice());
            }

            // Best-effort daily volume/value traded from the snapshot if available.
            if (snapshot != null) {
                builder.volume(snapshot.volume);

                if (snapshot.lastPrice != null) {
                    builder.valueTraded(snapshot.lastPrice.multiply(BigDecimal.valueOf(snapshot.volume)));
                }
            }
        }

        return builder.build();
    }

    private void updateSnapshot(String symbol, MarketDataMessage.Quote quote) {
        MarketDataSnapshot snapshot = snapshotCache.computeIfAbsent(symbol,
                k -> new MarketDataSnapshot());
        snapshot.bidPrice = quote.getBidPrice();
        snapshot.bidSize = quote.getBidSize();
        snapshot.askPrice = quote.getAskPrice();
        snapshot.askSize = quote.getAskSize();
        snapshot.lastUpdate = System.currentTimeMillis();
    }

    private BigDecimal calculateVWAP(String symbol) {
        return tradeRepository.calculateVWAP(symbol, LocalDate.now().atStartOfDay());
    }

    // ==================== Inner Classes ====================

    private static class MarketDataSnapshot {
        BigDecimal bidPrice;
        Long bidSize;
        BigDecimal askPrice;
        Long askSize;
        BigDecimal lastPrice;
        Long lastQuantity;
        Long volume;
        long lastUpdate;
    }
}
