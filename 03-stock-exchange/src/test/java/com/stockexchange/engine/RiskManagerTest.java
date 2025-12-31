package com.stockexchange.engine;

import com.stockexchange.entity.Account;
import com.stockexchange.entity.Order;
import com.stockexchange.entity.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for RiskManager.
 */
class RiskManagerTest {

    private RiskManager riskManager;

    @BeforeEach
    void setUp() {
        riskManager = new RiskManager();
    }

    @Nested
    @DisplayName("Basic Validation Tests")
    class BasicValidationTests {

        @Test
        @DisplayName("Should reject null order")
        void shouldRejectNullOrder() {
            Account account = createAccount();

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    null, account, null, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("INVALID_ORDER");
        }

        @Test
        @DisplayName("Should reject order with null symbol")
        void shouldRejectNullSymbol() {
            Order order = Order.builder()
                    .side(Order.Side.BUY)
                    .quantity(100L)
                    .orderType(Order.OrderType.LIMIT)
                    .price(new BigDecimal("150.00"))
                    .build();

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, createAccount(), null, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("INVALID_SYMBOL");
        }

        @Test
        @DisplayName("Should reject order with zero quantity")
        void shouldRejectZeroQuantity() {
            Order order = createOrder(Order.Side.BUY, 0, "150.00");

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, createAccount(), null, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("INVALID_QUANTITY");
        }

        @Test
        @DisplayName("Should reject limit order without price")
        void shouldRejectLimitOrderWithoutPrice() {
            Order order = Order.builder()
                    .symbol("AAPL")
                    .side(Order.Side.BUY)
                    .quantity(100L)
                    .orderType(Order.OrderType.LIMIT)
                    // No price set
                    .build();

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, createAccount(), null, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("INVALID_PRICE");
        }
    }

    @Nested
    @DisplayName("Account Validation Tests")
    class AccountValidationTests {

        @Test
        @DisplayName("Should reject when account is null")
        void shouldRejectNullAccount() {
            Order order = createOrder(Order.Side.BUY, 100, "150.00");

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, null, null, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("ACCOUNT_DISABLED");
        }

        @Test
        @DisplayName("Should reject when account cannot trade")
        void shouldRejectWhenAccountCannotTrade() {
            Order order = createOrder(Order.Side.BUY, 100, "150.00");
            Account account = createAccount();
            account.setCanTrade(false);

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, account, null, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("ACCOUNT_DISABLED");
        }
    }

    @Nested
    @DisplayName("Buying Power Tests")
    class BuyingPowerTests {

        @Test
        @DisplayName("Should approve buy order with sufficient buying power")
        void shouldApproveWithSufficientBuyingPower() {
            Order order = createOrder(Order.Side.BUY, 100, "150.00");
            Account account = createAccount();
            account.setBuyingPower(new BigDecimal("100000")); // $100K

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, account, null, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isTrue();
        }

        @Test
        @DisplayName("Should reject buy order with insufficient buying power")
        void shouldRejectWithInsufficientBuyingPower() {
            Order order = createOrder(Order.Side.BUY, 1000, "150.00"); // $150K order
            Account account = createAccount();
            account.setBuyingPower(new BigDecimal("100000")); // $100K available

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, account, null, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("INSUFFICIENT_BUYING_POWER");
        }

        @Test
        @DisplayName("Should not check buying power for sell orders")
        void shouldNotCheckBuyingPowerForSellOrders() {
            Order order = createOrder(Order.Side.SELL, 1000, "150.00");
            Account account = createAccount();
            account.setBuyingPower(BigDecimal.ZERO); // No buying power

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, account, null, new BigDecimal("150.00"));

            // Should pass - sells don't need buying power
            assertThat(result.isApproved()).isTrue();
        }
    }

    @Nested
    @DisplayName("Position Limit Tests")
    class PositionLimitTests {

        @Test
        @DisplayName("Should approve order within position limits")
        void shouldApproveWithinPositionLimits() {
            Order order = createOrder(Order.Side.BUY, 1000, "150.00");
            Account account = createAccount();
            account.setBuyingPower(new BigDecimal("1000000"));
            account.setMaxPositionSize(100000L);

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, account, null, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isTrue();
        }

        @Test
        @DisplayName("Should reject order exceeding position limits")
        void shouldRejectExceedingPositionLimits() {
            Order order = createOrder(Order.Side.BUY, 60000, "150.00");
            Account account = createAccount();
            account.setBuyingPower(new BigDecimal("100000000"));
            account.setMaxPositionSize(100000L);

            Position existingPosition = Position.builder()
                    .quantity(50000L)
                    .build();

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, account, existingPosition, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("POSITION_LIMIT_EXCEEDED");
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Tests")
    class CircuitBreakerTests {

        @Test
        @DisplayName("Should approve order within circuit breaker limits")
        void shouldApproveWithinCircuitBreaker() {
            Order order = createOrder(Order.Side.BUY, 100, "160.00"); // 6.67% above ref
            Account account = createAccount();
            account.setBuyingPower(new BigDecimal("100000"));

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, account, null, new BigDecimal("150.00")); // Reference price

            assertThat(result.isApproved()).isTrue();
        }

        @Test
        @DisplayName("Should reject order exceeding upper circuit breaker")
        void shouldRejectExceedingUpperCircuitBreaker() {
            Order order = createOrder(Order.Side.BUY, 100, "170.00"); // 13.33% above ref
            Account account = createAccount();
            account.setBuyingPower(new BigDecimal("100000"));

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, account, null, new BigDecimal("150.00")); // Reference price

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("CIRCUIT_BREAKER_UP");
        }

        @Test
        @DisplayName("Should reject order exceeding lower circuit breaker")
        void shouldRejectExceedingLowerCircuitBreaker() {
            Order order = createOrder(Order.Side.SELL, 100, "130.00"); // 13.33% below ref
            Account account = createAccount();
            account.setBuyingPower(new BigDecimal("100000"));

            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, account, null, new BigDecimal("150.00")); // Reference price

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("CIRCUIT_BREAKER_DOWN");
        }
    }

    @Nested
    @DisplayName("Rate Limiting Tests")
    class RateLimitingTests {

        @Test
        @DisplayName("Should approve orders within rate limit")
        void shouldApproveWithinRateLimit() {
            Account account = createAccount();
            account.setBuyingPower(new BigDecimal("100000000"));
            account.setRateLimitPerSecond(10);

            // Submit 5 orders (within limit of 10)
            for (int i = 0; i < 5; i++) {
                Order order = createOrder(Order.Side.BUY, 100, "150.00");
                order.setClientId((long) i);
                RiskManager.RiskCheckResult result = riskManager.checkOrder(
                        order, account, null, new BigDecimal("150.00"));
                assertThat(result.isApproved()).isTrue();
            }
        }

        @Test
        @DisplayName("Should reject orders exceeding rate limit")
        void shouldRejectExceedingRateLimit() {
            Account account = createAccount();
            account.setBuyingPower(new BigDecimal("100000000"));
            account.setRateLimitPerSecond(3);

            // Submit more than rate limit allows (same client)
            for (int i = 0; i < 3; i++) {
                Order order = createOrder(Order.Side.BUY, 100, "150.00");
                order.setClientId(1L); // Same client
                riskManager.checkOrder(order, account, null, new BigDecimal("150.00"));
            }

            // Next order should be rejected
            Order order = createOrder(Order.Side.BUY, 100, "150.00");
            order.setClientId(1L);
            RiskManager.RiskCheckResult result = riskManager.checkOrder(
                    order, account, null, new BigDecimal("150.00"));

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getRejectCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
        }
    }

    // Helper methods
    private Order createOrder(Order.Side side, long quantity, String price) {
        return Order.builder()
                .symbol("AAPL")
                .side(side)
                .orderType(Order.OrderType.LIMIT)
                .quantity(quantity)
                .price(new BigDecimal(price))
                .clientId(1L)
                .build();
    }

    private Account createAccount() {
        return Account.builder()
                .accountId(1L)
                .clientId(1L)
                .canTrade(true)
                .buyingPower(new BigDecimal("100000"))
                .maxPositionSize(100000L)
                .maxOrderValue(new BigDecimal("1000000"))
                .dailyLossLimit(new BigDecimal("50000"))
                .rateLimitPerSecond(100)
                .build();
    }
}
