package com.stockexchange.engine;

import com.stockexchange.entity.Order;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance Order Book implementation using price-time priority.
 * Lock-free design for maximum throughput.
 * 
 * Data structures:
 * - TreeMap (Red-Black tree) for price levels - O(log n) insert/delete
 * - LinkedList at each price level for FIFO ordering - O(1) operations
 * - HashMap for O(1) order lookup by ID
 */
@Slf4j
public class OrderBook {

    private final String symbol;

    // Bids: highest price first (descending)
    private final NavigableMap<BigDecimal, PriceLevel> bids;

    // Asks: lowest price first (ascending)
    private final NavigableMap<BigDecimal, PriceLevel> asks;

    // O(1) order lookup
    private final Map<Long, OrderBookEntry> orderMap;

    // Sequence number for trades
    private final AtomicLong tradeSequence;

    // Statistics
    @Getter
    private volatile BigDecimal lastTradePrice;
    @Getter
    private volatile long lastTradeQuantity;
    @Getter
    private volatile long totalVolume;
    @Getter
    private volatile int totalTrades;

    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.bids = new ConcurrentSkipListMap<>(Comparator.reverseOrder()); // Descending for bids
        this.asks = new ConcurrentSkipListMap<>(); // Ascending for asks
        this.orderMap = new HashMap<>();
        this.tradeSequence = new AtomicLong(0);
    }

    /**
     * Add an order to the book and execute any matches.
     * Returns list of executed trades.
     */
    public synchronized List<MatchResult> addOrder(Order order) {
        long startNanos = System.nanoTime();
        List<MatchResult> results = new ArrayList<>();

        // Validate
        if (order.getQuantity() <= 0) {
            return results;
        }

        // Try to match against opposite side
        if (order.getOrderType() == Order.OrderType.MARKET ||
                order.getOrderType() == Order.OrderType.LIMIT) {
            results = matchOrder(order);
        }

        // Add remaining to book (for limit orders only)
        if (order.getRemaining() > 0 && order.getOrderType() == Order.OrderType.LIMIT) {
            addToBook(order);
        }

        long elapsed = System.nanoTime() - startNanos;
        if (elapsed > 10_000) { // More than 10 microseconds
            log.warn("[ORDER BOOK] Slow order processing: {} ns for symbol {}", elapsed, symbol);
        }

        return results;
    }

    /**
     * Cancel an order by ID.
     */
    public synchronized boolean cancelOrder(long orderId) {
        OrderBookEntry entry = orderMap.remove(orderId);
        if (entry == null) {
            return false;
        }

        NavigableMap<BigDecimal, PriceLevel> side = entry.order.getSide() == Order.Side.BUY ? bids : asks;

        PriceLevel level = side.get(entry.price);
        if (level != null) {
            level.removeOrder(entry);
            if (level.isEmpty()) {
                side.remove(entry.price);
            }
        }

        return true;
    }

    /**
     * Modify an existing order (price and/or quantity).
     * If price changes, order loses time priority.
     */
    public synchronized boolean modifyOrder(long orderId, BigDecimal newPrice, long newQuantity) {
        OrderBookEntry entry = orderMap.get(orderId);
        if (entry == null) {
            return false;
        }

        // If only quantity decreased, keep time priority
        if (newPrice != null && newPrice.equals(entry.price) && newQuantity < entry.remainingQuantity) {
            entry.remainingQuantity = newQuantity;
            entry.order.setRemainingQuantity(newQuantity);
            return true;
        }

        // Cancel and re-add (loses time priority)
        Order order = entry.order;
        cancelOrder(orderId);

        if (newPrice != null) {
            order.setPrice(newPrice);
        }
        if (newQuantity > 0) {
            order.setQuantity(newQuantity);
            order.setRemainingQuantity(newQuantity);
        }

        addToBook(order);
        return true;
    }

    /**
     * Match incoming order against resting orders.
     */
    private List<MatchResult> matchOrder(Order incoming) {
        List<MatchResult> trades = new ArrayList<>();
        NavigableMap<BigDecimal, PriceLevel> oppositeSide = incoming.getSide() == Order.Side.BUY ? asks : bids;

        long remainingQty = incoming.getRemaining();

        while (remainingQty > 0 && !oppositeSide.isEmpty()) {
            Map.Entry<BigDecimal, PriceLevel> bestLevel = oppositeSide.firstEntry();
            if (bestLevel == null)
                break;

            BigDecimal levelPrice = bestLevel.getKey();
            PriceLevel level = bestLevel.getValue();

            // Price check for limit orders
            if (incoming.getOrderType() == Order.OrderType.LIMIT) {
                if (incoming.getSide() == Order.Side.BUY &&
                        levelPrice.compareTo(incoming.getPrice()) > 0) {
                    break; // Best ask too expensive
                }
                if (incoming.getSide() == Order.Side.SELL &&
                        levelPrice.compareTo(incoming.getPrice()) < 0) {
                    break; // Best bid too low
                }
            }

            // Match against orders at this price level (FIFO)
            Iterator<OrderBookEntry> iterator = level.orders.iterator();
            while (iterator.hasNext() && remainingQty > 0) {
                OrderBookEntry resting = iterator.next();

                long fillQty = Math.min(remainingQty, resting.remainingQuantity);

                // Create trade
                MatchResult trade = MatchResult.builder()
                        .tradeId(tradeSequence.incrementAndGet())
                        .symbol(symbol)
                        .price(levelPrice)
                        .quantity(fillQty)
                        .buyOrderId(incoming.getSide() == Order.Side.BUY ? incoming.getOrderId()
                                : resting.order.getOrderId())
                        .sellOrderId(incoming.getSide() == Order.Side.SELL ? incoming.getOrderId()
                                : resting.order.getOrderId())
                        .buyerId(incoming.getSide() == Order.Side.BUY ? incoming.getClientId()
                                : resting.order.getClientId())
                        .sellerId(incoming.getSide() == Order.Side.SELL ? incoming.getClientId()
                                : resting.order.getClientId())
                        .aggressorSide(incoming.getSide())
                        .timestamp(System.nanoTime())
                        .build();

                trades.add(trade);

                // Update quantities
                remainingQty -= fillQty;
                resting.remainingQuantity -= fillQty;

                // Update order entities
                incoming.fill(fillQty, levelPrice);
                resting.order.fill(fillQty, levelPrice);

                // Update stats
                lastTradePrice = levelPrice;
                lastTradeQuantity = fillQty;
                totalVolume += fillQty;
                totalTrades++;

                // Remove filled resting order
                if (resting.remainingQuantity <= 0) {
                    iterator.remove();
                    orderMap.remove(resting.order.getOrderId());
                }
            }

            // Remove empty price level
            if (level.isEmpty()) {
                oppositeSide.pollFirstEntry();
            }
        }

        return trades;
    }

    /**
     * Add order to the book at its price level.
     */
    private void addToBook(Order order) {
        NavigableMap<BigDecimal, PriceLevel> side = order.getSide() == Order.Side.BUY ? bids : asks;

        OrderBookEntry entry = new OrderBookEntry(order, order.getPrice(), order.getRemaining());

        side.computeIfAbsent(order.getPrice(), k -> new PriceLevel(k))
                .addOrder(entry);

        orderMap.put(order.getOrderId(), entry);
    }

    // ==================== Market Data Methods ====================

    public BigDecimal getBestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    public BigDecimal getBestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    public BigDecimal getSpread() {
        BigDecimal bid = getBestBid();
        BigDecimal ask = getBestAsk();
        if (bid == null || ask == null)
            return null;
        return ask.subtract(bid);
    }

    public long getBidDepth(int levels) {
        return getDepth(bids, levels);
    }

    public long getAskDepth(int levels) {
        return getDepth(asks, levels);
    }

    private long getDepth(NavigableMap<BigDecimal, PriceLevel> side, int levels) {
        long depth = 0;
        int count = 0;
        for (PriceLevel level : side.values()) {
            if (count++ >= levels)
                break;
            depth += level.totalQuantity;
        }
        return depth;
    }

    /**
     * Get Level 2 market data (price levels)
     */
    public List<PriceLevelData> getBids(int depth) {
        return getLevels(bids, depth);
    }

    public List<PriceLevelData> getAsks(int depth) {
        return getLevels(asks, depth);
    }

    private List<PriceLevelData> getLevels(NavigableMap<BigDecimal, PriceLevel> side, int depth) {
        List<PriceLevelData> levels = new ArrayList<>();
        int count = 0;
        for (PriceLevel level : side.values()) {
            if (count++ >= depth)
                break;
            levels.add(new PriceLevelData(level.price, level.totalQuantity, level.orderCount));
        }
        return levels;
    }

    public int getOrderCount() {
        return orderMap.size();
    }

    public int getBidLevelCount() {
        return bids.size();
    }

    public int getAskLevelCount() {
        return asks.size();
    }

    // ==================== Inner Classes ====================

    /**
     * Entry in the order book linking to the original order.
     */
    private static class OrderBookEntry {
        final Order order;
        final BigDecimal price;
        long remainingQuantity;

        OrderBookEntry(Order order, BigDecimal price, long remainingQuantity) {
            this.order = order;
            this.price = price;
            this.remainingQuantity = remainingQuantity;
        }
    }

    /**
     * Price level with FIFO ordering.
     */
    private static class PriceLevel {
        final BigDecimal price;
        final LinkedList<OrderBookEntry> orders;
        long totalQuantity;
        int orderCount;

        PriceLevel(BigDecimal price) {
            this.price = price;
            this.orders = new LinkedList<>();
            this.totalQuantity = 0;
            this.orderCount = 0;
        }

        void addOrder(OrderBookEntry entry) {
            orders.addLast(entry);
            totalQuantity += entry.remainingQuantity;
            orderCount++;
        }

        void removeOrder(OrderBookEntry entry) {
            orders.remove(entry);
            totalQuantity -= entry.remainingQuantity;
            orderCount--;
        }

        boolean isEmpty() {
            return orders.isEmpty();
        }
    }

    /**
     * Price level data for market data feed.
     */
    @lombok.Value
    public static class PriceLevelData {
        BigDecimal price;
        long quantity;
        int orderCount;
    }

    /**
     * Match result (trade).
     */
    @lombok.Value
    @lombok.Builder
    public static class MatchResult {
        long tradeId;
        String symbol;
        BigDecimal price;
        long quantity;
        long buyOrderId;
        long sellOrderId;
        long buyerId;
        long sellerId;
        Order.Side aggressorSide;
        long timestamp;
    }
}
