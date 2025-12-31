package com.stockexchange.engine;

import com.stockexchange.entity.Account;
import com.stockexchange.entity.Order;
import com.stockexchange.entity.Position;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pre-trade and Post-trade Risk Management.
 * 
 * Pre-trade checks:
 * - Position limits (max quantity per symbol)
 * - Order value limits
 * - Buying power / margin requirements
 * - Daily loss limits
 * - Rate limiting (orders per second)
 * - Price validation (circuit breakers)
 * 
 * Post-trade:
 * - P&L calculation
 * - Position updates
 * - Margin calls
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskManager {

    // Default risk limits
    private static final long DEFAULT_MAX_POSITION = 100_000;
    private static final BigDecimal DEFAULT_MAX_ORDER_VALUE = new BigDecimal("1000000");
    private static final BigDecimal DEFAULT_DAILY_LOSS_LIMIT = new BigDecimal("50000");
    private static final int DEFAULT_RATE_LIMIT = 100; // orders per second

    // Circuit breaker settings
    private static final BigDecimal CIRCUIT_BREAKER_UP = new BigDecimal("0.10"); // 10%
    private static final BigDecimal CIRCUIT_BREAKER_DOWN = new BigDecimal("0.10"); // 10%

    // Rate limiting: orders per client per second
    private final Map<Long, RateLimiter> clientRateLimiters = new ConcurrentHashMap<>();

    // Position tracking (in-memory for speed)
    private final Map<String, Long> positionCache = new ConcurrentHashMap<>(); // clientId:symbol -> qty

    // Daily P&L tracking
    private final Map<Long, BigDecimal> dailyPnL = new ConcurrentHashMap<>();

    /**
     * Perform all pre-trade risk checks.
     */
    public RiskCheckResult checkOrder(Order order, Account account, Position position,
            BigDecimal referencePrice) {
        long startNanos = System.nanoTime();

        try {
            // 1. Basic validation
            RiskCheckResult result = validateOrderBasics(order);
            if (result != null)
                return result;

            // 2. Account status check
            if (account == null || !account.isCanTrade()) {
                return reject("ACCOUNT_DISABLED", "Account is not enabled for trading");
            }

            // 3. Rate limiting
            result = checkRateLimit(order.getClientId(), account.getRateLimitPerSecond());
            if (result != null)
                return result;

            // 4. Buying power check
            result = checkBuyingPower(order, account, referencePrice);
            if (result != null)
                return result;

            // 5. Position limits
            result = checkPositionLimits(order, account, position);
            if (result != null)
                return result;

            // 6. Order value limits
            result = checkOrderValueLimits(order, account, referencePrice);
            if (result != null)
                return result;

            // 7. Daily loss limit
            result = checkDailyLossLimit(order.getClientId(), account);
            if (result != null)
                return result;

            // 8. Price circuit breakers
            if (order.getPrice() != null && referencePrice != null) {
                result = checkCircuitBreakers(order, referencePrice);
                if (result != null)
                    return result;
            }

            // All checks passed
            long latencyMicros = (System.nanoTime() - startNanos) / 1000;
            return RiskCheckResult.approved(latencyMicros);

        } catch (Exception e) {
            log.error("[RISK] Error checking order: {}", e.getMessage(), e);
            return reject("SYSTEM_ERROR", "Risk check failed: " + e.getMessage());
        }
    }

    private RiskCheckResult validateOrderBasics(Order order) {
        if (order == null) {
            return reject("INVALID_ORDER", "Order is null");
        }
        if (order.getSymbol() == null || order.getSymbol().isBlank()) {
            return reject("INVALID_SYMBOL", "Symbol is required");
        }
        if (order.getQuantity() <= 0) {
            return reject("INVALID_QUANTITY", "Quantity must be positive");
        }
        if (order.getSide() == null) {
            return reject("INVALID_SIDE", "Order side is required");
        }
        if (order.getOrderType() == Order.OrderType.LIMIT &&
                (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0)) {
            return reject("INVALID_PRICE", "Price required for limit orders");
        }
        return null;
    }

    private RiskCheckResult checkRateLimit(long clientId, Integer configuredLimit) {
        int limit = configuredLimit != null ? configuredLimit : DEFAULT_RATE_LIMIT;

        RateLimiter limiter = clientRateLimiters.computeIfAbsent(
                clientId, k -> new RateLimiter(limit));

        if (!limiter.tryAcquire()) {
            return reject("RATE_LIMIT_EXCEEDED",
                    "Order rate limit exceeded (" + limit + " orders/sec)");
        }
        return null;
    }

    private RiskCheckResult checkBuyingPower(Order order, Account account, BigDecimal price) {
        if (order.getSide() != Order.Side.BUY) {
            return null; // Only check buying power for buys
        }

        BigDecimal orderPrice = order.getPrice() != null ? order.getPrice() : price;
        if (orderPrice == null) {
            return reject("NO_PRICE", "Cannot determine order value without price");
        }

        BigDecimal orderValue = orderPrice.multiply(BigDecimal.valueOf(order.getQuantity()));
        BigDecimal buyingPower = account.getBuyingPower();

        if (buyingPower == null || orderValue.compareTo(buyingPower) > 0) {
            return reject("INSUFFICIENT_BUYING_POWER",
                    String.format("Insufficient buying power. Required: %s, Available: %s",
                            orderValue, buyingPower));
        }

        return null;
    }

    private RiskCheckResult checkPositionLimits(Order order, Account account, Position position) {
        long maxPosition = account.getMaxPositionSize() != null ? account.getMaxPositionSize() : DEFAULT_MAX_POSITION;

        long currentPosition = position != null ? position.getQuantity() : 0;
        long proposedPosition;

        if (order.getSide() == Order.Side.BUY) {
            proposedPosition = currentPosition + order.getQuantity();
        } else {
            proposedPosition = currentPosition - order.getQuantity();
        }

        if (Math.abs(proposedPosition) > maxPosition) {
            return reject("POSITION_LIMIT_EXCEEDED",
                    String.format("Position limit exceeded. Max: %d, Proposed: %d",
                            maxPosition, proposedPosition));
        }

        return null;
    }

    private RiskCheckResult checkOrderValueLimits(Order order, Account account, BigDecimal price) {
        BigDecimal maxOrderValue = account.getMaxOrderValue() != null ? account.getMaxOrderValue()
                : DEFAULT_MAX_ORDER_VALUE;

        BigDecimal orderPrice = order.getPrice() != null ? order.getPrice() : price;
        if (orderPrice == null) {
            return null; // Market order without reference price
        }

        BigDecimal orderValue = orderPrice.multiply(BigDecimal.valueOf(order.getQuantity()));

        if (orderValue.compareTo(maxOrderValue) > 0) {
            return reject("ORDER_VALUE_LIMIT_EXCEEDED",
                    String.format("Order value %s exceeds limit %s", orderValue, maxOrderValue));
        }

        return null;
    }

    private RiskCheckResult checkDailyLossLimit(long clientId, Account account) {
        BigDecimal lossLimit = account.getDailyLossLimit() != null ? account.getDailyLossLimit()
                : DEFAULT_DAILY_LOSS_LIMIT;

        BigDecimal currentLoss = dailyPnL.getOrDefault(clientId, BigDecimal.ZERO);

        if (currentLoss.compareTo(lossLimit.negate()) < 0) {
            return reject("DAILY_LOSS_LIMIT_EXCEEDED",
                    String.format("Daily loss limit reached. Limit: %s, Current: %s",
                            lossLimit, currentLoss));
        }

        return null;
    }

    private RiskCheckResult checkCircuitBreakers(Order order, BigDecimal referencePrice) {
        BigDecimal orderPrice = order.getPrice();
        if (orderPrice == null || referencePrice == null ||
                referencePrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal deviation = orderPrice.subtract(referencePrice)
                .divide(referencePrice, 4, RoundingMode.HALF_UP);

        // Check upper limit
        if (deviation.compareTo(CIRCUIT_BREAKER_UP) > 0) {
            return reject("CIRCUIT_BREAKER_UP",
                    String.format("Price %.2f exceeds upper circuit limit (%.0f%% above reference %.2f)",
                            orderPrice, CIRCUIT_BREAKER_UP.multiply(new BigDecimal("100")), referencePrice));
        }

        // Check lower limit
        if (deviation.compareTo(CIRCUIT_BREAKER_DOWN.negate()) < 0) {
            return reject("CIRCUIT_BREAKER_DOWN",
                    String.format("Price %.2f exceeds lower circuit limit (%.0f%% below reference %.2f)",
                            orderPrice, CIRCUIT_BREAKER_DOWN.multiply(new BigDecimal("100")), referencePrice));
        }

        return null;
    }

    // ==================== Post-Trade Risk ====================

    /**
     * Update risk metrics after a trade.
     */
    public void onTradeExecuted(OrderBook.MatchResult trade, Order buyOrder, Order sellOrder) {
        // Update positions
        updatePositionCache(trade.getBuyerId(), trade.getSymbol(), trade.getQuantity());
        updatePositionCache(trade.getSellerId(), trade.getSymbol(), -trade.getQuantity());

        log.debug("[RISK] Trade executed: {} {} @ {} qty {}",
                trade.getSymbol(), trade.getAggressorSide(), trade.getPrice(), trade.getQuantity());
    }

    /**
     * Record P&L for a client.
     */
    public void recordPnL(long clientId, BigDecimal pnl) {
        dailyPnL.merge(clientId, pnl, BigDecimal::add);
    }

    /**
     * Reset daily limits (call at market open).
     */
    public void resetDailyLimits() {
        dailyPnL.clear();
        log.info("[RISK] Daily limits reset");
    }

    private void updatePositionCache(long clientId, String symbol, long quantityDelta) {
        String key = clientId + ":" + symbol;
        positionCache.merge(key, quantityDelta, Long::sum);
    }

    private RiskCheckResult reject(String code, String reason) {
        return RiskCheckResult.rejected(code, reason);
    }

    // ==================== Inner Classes ====================

    /**
     * Simple sliding window rate limiter.
     */
    private static class RateLimiter {
        private final int limit;
        private final AtomicLong[] timestamps;
        private int index = 0;

        RateLimiter(int limit) {
            this.limit = limit;
            this.timestamps = new AtomicLong[limit];
            for (int i = 0; i < limit; i++) {
                timestamps[i] = new AtomicLong(0);
            }
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long oldest = timestamps[index].get();

            if (now - oldest < 1000) { // Within 1 second window
                return false;
            }

            timestamps[index].set(now);
            index = (index + 1) % limit;
            return true;
        }
    }

    /**
     * Result of risk check.
     */
    @lombok.Value
    public static class RiskCheckResult {
        boolean approved;
        String rejectCode;
        String rejectReason;
        long latencyMicros;

        public static RiskCheckResult approved(long latencyMicros) {
            return new RiskCheckResult(true, null, null, latencyMicros);
        }

        public static RiskCheckResult rejected(String code, String reason) {
            return new RiskCheckResult(false, code, reason, 0);
        }
    }
}
