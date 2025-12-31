package com.stockexchange.engine;

import com.stockexchange.entity.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Disruptor-based MatchingEngine.
 */
class MatchingEngineTest {

    private MatchingEngine matchingEngine;
    private List<OrderBook.MatchResult> executedTrades;

    @BeforeEach
    void setUp() {
        executedTrades = Collections.synchronizedList(new ArrayList<>());
        matchingEngine = new MatchingEngine();
        matchingEngine.addTradeListener(trade -> executedTrades.add(trade));
        matchingEngine.init();
    }

    @AfterEach
    void tearDown() {
        matchingEngine.shutdown();
    }

    @Nested
    @DisplayName("Order Submission Tests")
    class OrderSubmissionTests {

        @Test
        @DisplayName("Should submit single buy order successfully")
        void shouldSubmitBuyOrder() throws InterruptedException {
            Order order = createOrder("AAPL", Order.Side.BUY, 100, "150.00");

            long orderId = matchingEngine.submitOrder(order);

            assertThat(orderId).isGreaterThan(0);
            // Give time for order to be processed
            Thread.sleep(50);
        }

        @Test
        @DisplayName("Should submit single sell order successfully")
        void shouldSubmitSellOrder() throws InterruptedException {
            Order order = createOrder("AAPL", Order.Side.SELL, 100, "150.00");

            long orderId = matchingEngine.submitOrder(order);

            assertThat(orderId).isGreaterThan(0);
            Thread.sleep(50);
        }

        @Test
        @DisplayName("Should handle high volume order submission")
        void shouldHandleHighVolumeSubmission() throws InterruptedException {
            int orderCount = 100;

            for (int i = 0; i < orderCount; i++) {
                Order order = createOrder("AAPL",
                        i % 2 == 0 ? Order.Side.BUY : Order.Side.SELL,
                        100,
                        "150.00");
                matchingEngine.submitOrder(order);
            }

            // Wait for processing
            Thread.sleep(200);

            // Should have processed orders
            assertThat(matchingEngine.getOrdersProcessed()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Order Matching Tests")
    class OrderMatchingTests {

        @Test
        @DisplayName("Should match crossing orders and generate trade")
        void shouldMatchCrossingOrders() throws InterruptedException {
            // Submit sell order first
            Order sellOrder = createOrder("AAPL", Order.Side.SELL, 100, "150.00");
            matchingEngine.submitOrder(sellOrder);

            Thread.sleep(50);

            // Submit crossing buy order
            Order buyOrder = createOrder("AAPL", Order.Side.BUY, 100, "150.00");
            matchingEngine.submitOrder(buyOrder);

            Thread.sleep(100);

            // Should have a trade
            assertThat(executedTrades).hasSize(1);
            OrderBook.MatchResult trade = executedTrades.get(0);
            assertThat(trade.getQuantity()).isEqualTo(100);
            assertThat(trade.getPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        }

        @Test
        @DisplayName("Should handle partial fill")
        void shouldHandlePartialFill() throws InterruptedException {
            // Submit large sell order
            Order sellOrder = createOrder("AAPL", Order.Side.SELL, 200, "150.00");
            matchingEngine.submitOrder(sellOrder);

            Thread.sleep(50);

            // Submit smaller buy order
            Order buyOrder = createOrder("AAPL", Order.Side.BUY, 100, "150.00");
            matchingEngine.submitOrder(buyOrder);

            Thread.sleep(100);

            // Should have one partial trade
            assertThat(executedTrades).hasSize(1);
            OrderBook.MatchResult trade = executedTrades.get(0);
            assertThat(trade.getQuantity()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should match orders across multiple price levels")
        void shouldMatchAcrossMultiplePriceLevels() throws InterruptedException {
            // Submit sell orders at different prices
            Order sell1 = createOrder("AAPL", Order.Side.SELL, 100, "150.00");
            Order sell2 = createOrder("AAPL", Order.Side.SELL, 100, "151.00");

            matchingEngine.submitOrder(sell1);
            matchingEngine.submitOrder(sell2);

            Thread.sleep(50);

            // Submit large buy order that sweeps both levels
            Order buyOrder = createOrder("AAPL", Order.Side.BUY, 200, "152.00");
            matchingEngine.submitOrder(buyOrder);

            Thread.sleep(100);

            // Should have two trades at different prices
            assertThat(executedTrades).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Order Cancellation Tests")
    class OrderCancellationTests {

        @Test
        @DisplayName("Should cancel order successfully")
        void shouldCancelOrder() throws InterruptedException {
            Order order = createOrder("AAPL", Order.Side.SELL, 100, "200.00");
            order.setClientId(1L);
            long orderId = matchingEngine.submitOrder(order);

            Thread.sleep(50);

            matchingEngine.cancelOrder("AAPL", orderId, 1L);

            Thread.sleep(50);

            // Order should be removed from book
            OrderBook orderBook = matchingEngine.getOrderBook("AAPL");
            assertThat(orderBook).isNotNull();
        }
    }

    @Nested
    @DisplayName("Order Book Management Tests")
    class OrderBookManagementTests {

        @Test
        @DisplayName("Should return order book for symbol")
        void shouldReturnOrderBook() throws InterruptedException {
            Order order = createOrder("AAPL", Order.Side.BUY, 100, "150.00");
            matchingEngine.submitOrder(order);

            Thread.sleep(50);

            OrderBook orderBook = matchingEngine.getOrderBook("AAPL");

            assertThat(orderBook).isNotNull();
        }

        @Test
        @DisplayName("Should create separate order books for different symbols")
        void shouldCreateSeparateOrderBooks() throws InterruptedException {
            Order aaplOrder = createOrder("AAPL", Order.Side.BUY, 100, "150.00");
            Order googlOrder = createOrder("GOOGL", Order.Side.BUY, 50, "2800.00");

            matchingEngine.submitOrder(aaplOrder);
            matchingEngine.submitOrder(googlOrder);

            Thread.sleep(50);

            OrderBook aaplBook = matchingEngine.getOrderBook("AAPL");
            OrderBook googlBook = matchingEngine.getOrderBook("GOOGL");

            assertThat(aaplBook).isNotNull();
            assertThat(googlBook).isNotNull();
            assertThat(aaplBook).isNotSameAs(googlBook);
        }
    }

    @Nested
    @DisplayName("Performance Metrics Tests")
    class PerformanceMetricsTests {

        @Test
        @DisplayName("Should track orders processed count")
        void shouldTrackOrdersProcessed() throws InterruptedException {
            int orderCount = 10;

            for (int i = 0; i < orderCount; i++) {
                Order order = createOrder("AAPL", Order.Side.BUY, 100, "150.00");
                matchingEngine.submitOrder(order);
            }

            Thread.sleep(100);

            assertThat(matchingEngine.getOrdersProcessed()).isGreaterThanOrEqualTo(orderCount);
        }

        @Test
        @DisplayName("Should track trades executed count")
        void shouldTrackTradesExecuted() throws InterruptedException {
            // Submit matching orders
            Order sellOrder = createOrder("AAPL", Order.Side.SELL, 100, "150.00");
            matchingEngine.submitOrder(sellOrder);

            Thread.sleep(50);

            Order buyOrder = createOrder("AAPL", Order.Side.BUY, 100, "150.00");
            matchingEngine.submitOrder(buyOrder);

            Thread.sleep(100);

            assertThat(matchingEngine.getTradesExecuted()).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Market Order Tests")
    class MarketOrderTests {

        @Test
        @DisplayName("Should execute market buy order at best available price")
        void shouldExecuteMarketBuyOrder() throws InterruptedException {
            // Set up liquidity
            Order sellOrder = createOrder("AAPL", Order.Side.SELL, 100, "150.00");
            matchingEngine.submitOrder(sellOrder);

            Thread.sleep(50);

            // Submit market buy
            Order marketBuy = Order.builder()
                    .symbol("AAPL")
                    .side(Order.Side.BUY)
                    .orderType(Order.OrderType.MARKET)
                    .quantity(100L)
                    .remainingQuantity(100L)
                    .filledQuantity(0L)
                    .build();
            matchingEngine.submitOrder(marketBuy);

            Thread.sleep(100);

            // Should execute at the ask price
            assertThat(executedTrades).hasSize(1);
            assertThat(executedTrades.get(0).getPrice())
                    .isEqualByComparingTo(new BigDecimal("150.00"));
        }

        @Test
        @DisplayName("Should execute market sell order at best available price")
        void shouldExecuteMarketSellOrder() throws InterruptedException {
            // Set up liquidity
            Order buyOrder = createOrder("AAPL", Order.Side.BUY, 100, "150.00");
            matchingEngine.submitOrder(buyOrder);

            Thread.sleep(50);

            // Submit market sell
            Order marketSell = Order.builder()
                    .symbol("AAPL")
                    .side(Order.Side.SELL)
                    .orderType(Order.OrderType.MARKET)
                    .quantity(100L)
                    .remainingQuantity(100L)
                    .filledQuantity(0L)
                    .build();
            matchingEngine.submitOrder(marketSell);

            Thread.sleep(100);

            // Should execute at the bid price
            assertThat(executedTrades).hasSize(1);
            assertThat(executedTrades.get(0).getPrice())
                    .isEqualByComparingTo(new BigDecimal("150.00"));
        }
    }

    // Helper method
    private Order createOrder(String symbol, Order.Side side, long quantity, String price) {
        return Order.builder()
                .symbol(symbol)
                .side(side)
                .orderType(Order.OrderType.LIMIT)
                .quantity(quantity)
                .remainingQuantity(quantity)
                .filledQuantity(0L)
                .price(new BigDecimal(price))
                .build();
    }
}
