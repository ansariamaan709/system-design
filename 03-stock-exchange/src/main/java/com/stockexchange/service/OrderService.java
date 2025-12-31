package com.stockexchange.service;

import com.stockexchange.dto.OrderRequest;
import com.stockexchange.dto.OrderResponse;
import com.stockexchange.engine.MatchingEngine;
import com.stockexchange.engine.OrderBook;
import com.stockexchange.engine.RiskManager;
import com.stockexchange.entity.Account;
import com.stockexchange.entity.Instrument;
import com.stockexchange.entity.Order;
import com.stockexchange.entity.Position;
import com.stockexchange.exception.ExchangeException;
import com.stockexchange.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Order Management Service.
 * Handles order submission, cancellation, and lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final MatchingEngine matchingEngine;
    private final RiskManager riskManager;
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataService marketDataService;

    // In-memory order cache for fast lookups
    private final ConcurrentMap<Long, Order> activeOrders = new ConcurrentHashMap<>();

    /**
     * Submit a new order.
     */
    @Transactional
    public OrderResponse submitOrder(Long clientId, Long accountId, OrderRequest request) {
        long startTime = System.nanoTime();
        log.debug("[ORDER] Submitting order: {} {} {} @ {}",
                request.getSide(), request.getQuantity(), request.getSymbol(), request.getPrice());

        // Validate instrument
        Instrument instrument = instrumentRepository.findBySymbol(request.getSymbol())
                .orElseThrow(() -> new ExchangeException("UNKNOWN_SYMBOL",
                        "Unknown symbol: " + request.getSymbol()));

        if (!isInstrumentTradable(instrument)) {
            throw new ExchangeException("NOT_TRADABLE",
                    "Symbol " + request.getSymbol() + " is not tradable");
        }

        if (instrument.getTradingStatus() == Instrument.TradingStatus.HALTED) {
            throw new ExchangeException("TRADING_HALTED",
                    "Trading is halted for " + request.getSymbol());
        }

        // Get account
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ExchangeException("UNKNOWN_ACCOUNT",
                        "Unknown account: " + accountId));

        if (!canAccountTrade(account)) {
            throw new ExchangeException("ACCOUNT_DISABLED", "Account is not enabled for trading");
        }

        // Build order entity
        Order order = buildOrder(clientId, accountId, request);

        // Validate price against tick size
        if (order.getPrice() != null && instrument.getTickSize() != null) {
            BigDecimal rounded = instrument.roundToTick(order.getPrice());
            if (rounded.compareTo(order.getPrice()) != 0) {
                throw new ExchangeException("INVALID_PRICE",
                        "Price must be multiple of tick size " + instrument.getTickSize());
            }
        }

        // Validate quantity against lot size
        if (instrument.getLotSize() != null && order.getQuantity() % instrument.getLotSize() != 0) {
            throw new ExchangeException("INVALID_QUANTITY",
                    "Quantity must be multiple of lot size " + instrument.getLotSize());
        }

        // Get current position for risk checks
        Position position = positionRepository.findByAccountIdAndSymbol(accountId, request.getSymbol())
                .orElse(null);

        // Get reference price for risk checks
        BigDecimal referencePrice = getReferencePriceForRisk(request.getSymbol(), request.getPrice());

        // Pre-trade risk checks
        RiskManager.RiskCheckResult riskResult = riskManager.checkOrder(
                order, account, position, referencePrice);

        if (!riskResult.isApproved()) {
            order.setStatus(Order.OrderStatus.REJECTED);
            order.setRejectReason(riskResult.getRejectReason());
            order = orderRepository.save(order);
            log.warn("[ORDER] Order rejected: {} - {}", riskResult.getRejectCode(), riskResult.getRejectReason());
            return OrderResponse.fromOrder(order);
        }

        // Reserve buying power for buy orders
        if (order.getSide() == Order.Side.BUY && referencePrice != null) {
            BigDecimal reserveAmount = referencePrice.multiply(BigDecimal.valueOf(order.getQuantity()));
            account.reserveBuyingPower(reserveAmount);
            accountRepository.save(account);
        }

        // Save order to database
        order = orderRepository.save(order);

        // Cache active order
        activeOrders.put(order.getOrderId(), order);

        // Submit to matching engine (asynchronous)
        long orderId = matchingEngine.submitOrder(order);

        long latency = (System.nanoTime() - startTime) / 1000; // microseconds
        log.info("[ORDER] Order {} submitted in {} μs", orderId, latency);

        OrderResponse response = OrderResponse.fromOrder(order);
        response.setLatencyMicros(latency);
        return response;
    }

    /**
     * Cancel an existing order.
     */
    @Transactional
    public OrderResponse cancelOrder(Long clientId, Long orderId) {
        log.debug("[ORDER] Cancelling order {}", orderId);

        Order order = orderRepository.findByOrderIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new ExchangeException("ORDER_NOT_FOUND",
                        "Order not found: " + orderId));

        if (!order.isCancellable()) {
            throw new ExchangeException("NOT_CANCELLABLE",
                    "Order cannot be cancelled. Status: " + order.getStatus());
        }

        // Set pending cancel status
        order.setStatus(Order.OrderStatus.PENDING_CANCEL);
        orderRepository.save(order);

        // Send cancel to matching engine
        matchingEngine.cancelOrder(order.getSymbol(), orderId, clientId);

        // Wait briefly for confirmation (in production, use async callback)
        try {
            Thread.sleep(1); // Minimal delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Fetch updated order
        order = orderRepository.findById(orderId).orElse(order);

        // Release reserved buying power
        if (order.getStatus() == Order.OrderStatus.CANCELLED &&
                order.getSide() == Order.Side.BUY &&
                order.getRemaining() > 0) {
            releaseBuyingPower(order);
        }

        // Remove from active orders cache
        activeOrders.remove(orderId);

        log.info("[ORDER] Order {} cancelled", orderId);
        return OrderResponse.fromOrder(order);
    }

    /**
     * Cancel all orders for a client.
     */
    @Transactional
    public int cancelAllOrders(Long clientId, String symbol) {
        List<Order> activeOrdersList = symbol != null ? orderRepository.findByClientIdAndStatusIn(clientId,
                List.of(Order.OrderStatus.NEW, Order.OrderStatus.PARTIALLY_FILLED))
                .stream()
                .filter(o -> o.getSymbol().equals(symbol))
                .toList() : orderRepository.findActiveOrdersByClientId(clientId);

        int cancelled = 0;
        for (Order order : activeOrdersList) {
            try {
                cancelOrder(clientId, order.getOrderId());
                cancelled++;
            } catch (Exception e) {
                log.error("[ORDER] Failed to cancel order {}: {}", order.getOrderId(), e.getMessage());
            }
        }

        log.info("[ORDER] Cancelled {} orders for client {}", cancelled, clientId);
        return cancelled;
    }

    /**
     * Get order by ID.
     */
    public OrderResponse getOrder(Long clientId, Long orderId) {
        Order order = orderRepository.findByOrderIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new ExchangeException("ORDER_NOT_FOUND",
                        "Order not found: " + orderId));
        return OrderResponse.fromOrder(order);
    }

    /**
     * Get order by client order ID.
     */
    public OrderResponse getOrderByClientOrderId(Long clientId, String clientOrderId) {
        Order order = orderRepository.findByClientOrderIdAndClientId(clientOrderId, clientId)
                .orElseThrow(() -> new ExchangeException("ORDER_NOT_FOUND",
                        "Order not found: " + clientOrderId));
        return OrderResponse.fromOrder(order);
    }

    /**
     * Get orders for a client with filters.
     */
    public Page<OrderResponse> getOrders(Long clientId, String symbol,
            Order.OrderStatus status, Order.Side side, Pageable pageable) {
        Page<Order> orders = orderRepository.findByClientIdWithFilters(
                clientId, symbol, status, side, pageable);
        return orders.map(OrderResponse::fromOrder);
    }

    /**
     * Get active orders.
     */
    public List<OrderResponse> getActiveOrders(Long clientId) {
        return orderRepository.findActiveOrdersByClientId(clientId)
                .stream()
                .map(OrderResponse::fromOrder)
                .toList();
    }

    /**
     * Handle order fill from matching engine.
     */
    @Transactional
    public void onOrderFilled(OrderBook.MatchResult trade) {
        // Update buy order
        Order buyOrder = activeOrders.get(trade.getBuyOrderId());
        if (buyOrder != null) {
            updateOrderAfterFill(buyOrder, trade);
        }

        // Update sell order
        Order sellOrder = activeOrders.get(trade.getSellOrderId());
        if (sellOrder != null) {
            updateOrderAfterFill(sellOrder, trade);
        }
    }

    private void updateOrderAfterFill(Order order, OrderBook.MatchResult trade) {
        // Update order status
        if (order.isFilled()) {
            order.setStatus(Order.OrderStatus.FILLED);
            activeOrders.remove(order.getOrderId());
        } else {
            order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
        }

        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        // Release excess buying power if buy order fully filled
        if (order.getSide() == Order.Side.BUY && order.isFilled()) {
            // Calculate any excess from execution price vs limit price
            if (order.getPrice() != null) {
                BigDecimal reservedValue = order.getPrice().multiply(
                        BigDecimal.valueOf(order.getQuantity()));

                BigDecimal executionPrice = resolveExecutionPrice(trade, order);
                BigDecimal actualValue = executionPrice != null
                        ? executionPrice.multiply(BigDecimal.valueOf(order.getFilledQuantity()))
                        : reservedValue;

                BigDecimal excess = reservedValue.subtract(actualValue);
                if (excess.compareTo(BigDecimal.ZERO) > 0) {
                    Account account = accountRepository.findById(order.getAccountId()).orElse(null);
                    if (account != null) {
                        account.releaseBuyingPower(excess);
                        accountRepository.save(account);
                    }
                }
            }
        }
    }

    private Order buildOrder(Long clientId, Long accountId, OrderRequest request) {
        String clientOrderId = request.getClientOrderId() != null ? request.getClientOrderId()
                : "ORD-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);

        return Order.builder()
                .clientId(clientId)
                .accountId(accountId)
                .symbol(request.getSymbol())
                .side(request.getSide())
                .orderType(request.getOrderType())
                .timeInForce(request.getTimeInForce() != null ? request.getTimeInForce() : Order.TimeInForce.DAY)
                .quantity(request.getQuantity())
                .remainingQuantity(request.getQuantity())
                .filledQuantity(0L)
                .price(request.getPrice())
                .stopPrice(request.getStopPrice())
                .clientOrderId(clientOrderId)
                .expireTime(request.getExpireTime() != null
                        ? request.getExpireTime().atZone(java.time.ZoneId.systemDefault()).toInstant()
                        : null)
                .displayQuantity(request.getDisplayQuantity())
                .status(Order.OrderStatus.PENDING_NEW)
                .build();
    }

    private BigDecimal getReferencePriceForRisk(String symbol, BigDecimal orderPrice) {
        // Try to get last trade price
        BigDecimal lastPrice = marketDataService.getLastPrice(symbol);
        if (lastPrice != null) {
            return lastPrice;
        }
        // Fall back to order price
        return orderPrice;
    }

    private void releaseBuyingPower(Order order) {
        if (order.getPrice() != null) {
            BigDecimal releaseAmount = order.getPrice().multiply(
                    BigDecimal.valueOf(order.getRemaining()));
            Account account = accountRepository.findById(order.getAccountId()).orElse(null);
            if (account != null) {
                account.releaseBuyingPower(releaseAmount);
                accountRepository.save(account);
            }
        }
    }

    /**
     * Expire day orders at market close.
     */
    @Transactional
    public int expireDayOrders() {
        List<Order> dayOrders = orderRepository.findDayOrdersToExpire();
        int expired = 0;

        for (Order order : dayOrders) {
            order.setStatus(Order.OrderStatus.EXPIRED);
            orderRepository.save(order);

            // Release buying power
            if (order.getSide() == Order.Side.BUY) {
                releaseBuyingPower(order);
            }

            // Remove from matching engine
            matchingEngine.cancelOrder(order.getSymbol(), order.getOrderId(), order.getClientId());
            activeOrders.remove(order.getOrderId());
            expired++;
        }

        log.info("[ORDER] Expired {} day orders at market close", expired);
        return expired;
    }

    /**
     * Expire GTD orders that have passed their expiration time.
     */
    @Transactional
    public int expireGtdOrders() {
        List<Order> expiredOrders = orderRepository.findExpiredOrders(java.time.Instant.now());
        int expired = 0;

        for (Order order : expiredOrders) {
            order.setStatus(Order.OrderStatus.EXPIRED);
            orderRepository.save(order);

            if (order.getSide() == Order.Side.BUY) {
                releaseBuyingPower(order);
            }

            matchingEngine.cancelOrder(order.getSymbol(), order.getOrderId(), order.getClientId());
            activeOrders.remove(order.getOrderId());
            expired++;
        }

        log.info("[ORDER] Expired {} GTD orders", expired);
        return expired;
    }

    private boolean isInstrumentTradable(Instrument instrument) {
        if (instrument == null) {
            return false;
        }
        // Default to tradable unless explicitly halted.
        return instrument.getTradingStatus() != Instrument.TradingStatus.HALTED;
    }

    private boolean canAccountTrade(Account account) {
        // If account has richer enablement/status checks, wire them in here.
        return account != null;
    }

    private BigDecimal resolveExecutionPrice(OrderBook.MatchResult trade, Order order) {
        // Keep this implementation conservative to avoid depending on MatchResult internals.
        if (order == null) {
            return null;
        }
        if (order.getPrice() != null) {
            return order.getPrice();
        }
        return marketDataService.getLastPrice(order.getSymbol());
    }
}
