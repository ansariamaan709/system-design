package com.stockexchange.engine;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.stockexchange.entity.Order;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance Matching Engine using LMAX Disruptor.
 * 
 * Architecture:
 * - Single-threaded event processing (no locks)
 * - Lock-free ring buffer for order intake
 * - Separate order book per symbol for parallelism
 * - Batch event handling for throughput
 * 
 * Performance targets:
 * - Order latency: < 100 microseconds p99
 * - Throughput: > 100,000 orders/second
 */
@Slf4j
@Component
public class MatchingEngine {

    // Ring buffer size (must be power of 2)
    private static final int BUFFER_SIZE = 1024 * 64; // 64K slots

    // Order books by symbol
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    // Disruptor for order sequencing
    private Disruptor<OrderEvent> disruptor;
    private RingBuffer<OrderEvent> ringBuffer;

    // Listeners for trade events
    private final List<TradeListener> tradeListeners = new CopyOnWriteArrayList<>();

    // Statistics
    private final AtomicLong ordersProcessed = new AtomicLong(0);
    private final AtomicLong tradesExecuted = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);

    // Sequence number for orders
    private final AtomicLong orderSequence = new AtomicLong(System.currentTimeMillis() * 1000);

    @PostConstruct
    public void init() {
        log.info("[MATCHING ENGINE] Initializing with buffer size {}", BUFFER_SIZE);

        // Create thread factory with high priority
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "matching-engine");
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        };

        // Create disruptor with busy spin wait strategy for lowest latency
        disruptor = new Disruptor<>(
                OrderEvent::new,
                BUFFER_SIZE,
                threadFactory,
                ProducerType.MULTI,
                new BusySpinWaitStrategy() // Lowest latency wait strategy
        );

        // Set up event handler
        disruptor.handleEventsWith(this::handleOrderEvent);

        // Handle exceptions
        disruptor.setDefaultExceptionHandler(new ExceptionHandler<OrderEvent>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, OrderEvent event) {
                Long orderId = (event != null && event.order != null) ? event.order.getOrderId() : null;
                log.error("[MATCHING ENGINE] Error processing order {}: {}",
                        orderId, ex.getMessage(), ex);
            }

            @Override
            public void handleOnStartException(Throwable ex) {
                log.error("[MATCHING ENGINE] Error on start", ex);
            }

            @Override
            public void handleOnShutdownException(Throwable ex) {
                log.error("[MATCHING ENGINE] Error on shutdown", ex);
            }
        });

        // Start the disruptor
        ringBuffer = disruptor.start();

        log.info("[MATCHING ENGINE] Started successfully");
    }

    @PreDestroy
    public void shutdown() {
        log.info("[MATCHING ENGINE] Shutting down...");
        if (disruptor != null) {
            disruptor.shutdown();
        }
        log.info("[MATCHING ENGINE] Shutdown complete. Processed {} orders, executed {} trades",
                ordersProcessed.get(), tradesExecuted.get());
    }

    /**
     * Submit a new order to the matching engine.
     * Non-blocking, returns immediately after publishing to ring buffer.
     */
    public long submitOrder(Order order) {
        long orderId = orderSequence.incrementAndGet();
        order.setOrderId(orderId);
        order.setSubmitTime(System.nanoTime());
        order.setStatus(Order.OrderStatus.PENDING_NEW);

        // Publish to ring buffer
        long sequence = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(sequence);
            event.populate(OrderEventType.NEW, order, System.nanoTime());
        } finally {
            ringBuffer.publish(sequence);
        }

        return orderId;
    }

    /**
     * Cancel an existing order.
     */
    public void cancelOrder(String symbol, long orderId, long clientId) {
        Order cancelOrder = Order.builder()
                .orderId(orderId)
                .symbol(symbol)
                .clientId(clientId)
                .build();

        long sequence = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(sequence);
            event.populate(OrderEventType.CANCEL, cancelOrder, System.nanoTime());
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * Modify an existing order.
     */
    public void modifyOrder(String symbol, long orderId, BigDecimal newPrice, long newQuantity) {
        Order modifyOrder = Order.builder()
                .orderId(orderId)
                .symbol(symbol)
                .price(newPrice)
                .quantity(newQuantity)
                .build();

        long sequence = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(sequence);
            event.populate(OrderEventType.MODIFY, modifyOrder, System.nanoTime());
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * Handle order event from disruptor (single-threaded).
     */
    private void handleOrderEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        try {
            OrderBook orderBook = getOrCreateOrderBook(event.order.getSymbol());

            switch (event.eventType) {
                case NEW -> processNewOrder(orderBook, event.order);
                case CANCEL -> processCancelOrder(orderBook, event.order);
                case MODIFY -> processModifyOrder(orderBook, event.order);
            }

            // Track latency
            long latencyNanos = System.nanoTime() - event.timestamp;
            totalLatencyNanos.addAndGet(latencyNanos);
            ordersProcessed.incrementAndGet();

            // Log slow orders
            if (latencyNanos > 100_000) { // > 100 microseconds
                log.warn("[MATCHING ENGINE] Slow order: {} ns for order {}",
                        latencyNanos, event.order.getOrderId());
            }

        } catch (Exception e) {
            log.error("[MATCHING ENGINE] Error processing event: {}", e.getMessage(), e);
            if (event.order != null) {
                event.order.setStatus(Order.OrderStatus.REJECTED);
                event.order.setRejectReason(e.getMessage());
            }
        }

        // Clear event for reuse
        event.clear();
    }

    private void processNewOrder(OrderBook orderBook, Order order) {
        order.setStatus(Order.OrderStatus.NEW);
        order.setMatchTime(System.nanoTime());

        List<OrderBook.MatchResult> trades = orderBook.addOrder(order);

        // Notify listeners of trades
        for (OrderBook.MatchResult trade : trades) {
            tradesExecuted.incrementAndGet();
            notifyTradeListeners(trade);
        }

        // Update order status based on fill
        if (order.isFilled()) {
            order.setStatus(Order.OrderStatus.FILLED);
        } else if (order.getFilledQuantity() > 0) {
            order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
        }
    }

    private void processCancelOrder(OrderBook orderBook, Order order) {
        boolean cancelled = orderBook.cancelOrder(order.getOrderId());
        if (cancelled) {
            order.setStatus(Order.OrderStatus.CANCELLED);
            log.debug("[MATCHING ENGINE] Cancelled order {}", order.getOrderId());
        } else {
            log.warn("[MATCHING ENGINE] Order {} not found for cancellation", order.getOrderId());
        }
    }

    private void processModifyOrder(OrderBook orderBook, Order order) {
        boolean modified = orderBook.modifyOrder(
                order.getOrderId(),
                order.getPrice(),
                order.getQuantity());
        if (modified) {
            log.debug("[MATCHING ENGINE] Modified order {}", order.getOrderId());
        } else {
            log.warn("[MATCHING ENGINE] Order {} not found for modification", order.getOrderId());
        }
    }

    private OrderBook getOrCreateOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, OrderBook::new);
    }

    // ==================== Trade Listeners ====================

    public void addTradeListener(TradeListener listener) {
        tradeListeners.add(listener);
    }

    public void removeTradeListener(TradeListener listener) {
        tradeListeners.remove(listener);
    }

    private void notifyTradeListeners(OrderBook.MatchResult trade) {
        for (TradeListener listener : tradeListeners) {
            try {
                listener.onTrade(trade);
            } catch (Exception e) {
                log.error("[MATCHING ENGINE] Error notifying trade listener: {}", e.getMessage());
            }
        }
    }

    // ==================== Market Data ====================

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.get(symbol);
    }

    public BigDecimal getBestBid(String symbol) {
        OrderBook book = orderBooks.get(symbol);
        return book != null ? book.getBestBid() : null;
    }

    public BigDecimal getBestAsk(String symbol) {
        OrderBook book = orderBooks.get(symbol);
        return book != null ? book.getBestAsk() : null;
    }

    // ==================== Statistics ====================

    public long getOrdersProcessed() {
        return ordersProcessed.get();
    }

    public long getTradesExecuted() {
        return tradesExecuted.get();
    }

    public double getAverageLatencyMicros() {
        long processed = ordersProcessed.get();
        if (processed == 0)
            return 0;
        return (totalLatencyNanos.get() / processed) / 1000.0;
    }

    public long getRingBufferCapacity() {
        return ringBuffer.getBufferSize();
    }

    public long getRingBufferRemaining() {
        return ringBuffer.remainingCapacity();
    }

    // ==================== Inner Classes ====================

    public enum OrderEventType {
        NEW, CANCEL, MODIFY
    }

    /**
     * Pre-allocated event for ring buffer.
     */
    public static class OrderEvent {
        OrderEventType eventType;
        Order order;
        long timestamp;

        void populate(OrderEventType eventType, Order order, long timestamp) {
            this.eventType = eventType;
            this.order = order;
            this.timestamp = timestamp;
        }

        void clear() {
            this.eventType = null;
            this.order = null;
            this.timestamp = 0;
        }
    }

    /**
     * Trade event listener interface.
     */
    @FunctionalInterface
    public interface TradeListener {
        void onTrade(OrderBook.MatchResult trade);
    }
}
