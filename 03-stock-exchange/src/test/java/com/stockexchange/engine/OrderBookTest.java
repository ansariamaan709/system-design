package com.stockexchange.engine;

import com.stockexchange.entity.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for OrderBook - the core matching engine component.
 */
class OrderBookTest {

    private OrderBook orderBook;
    private static final String SYMBOL = "AAPL";

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook(SYMBOL);
    }

    @Nested
    @DisplayName("Limit Order Tests")
    class LimitOrderTests {

        @Test
        @DisplayName("Should add limit buy order to bids")
        void shouldAddBuyOrderToBids() {
            Order order = createLimitOrder(1L, Order.Side.BUY, 100, "150.00");

            List<OrderBook.MatchResult> results = orderBook.addOrder(order);

            assertThat(results).isEmpty();
            assertThat(orderBook.getBestBid()).isEqualByComparingTo("150.00");
            assertThat(orderBook.getBidDepth(1)).isEqualTo(100);
            assertThat(orderBook.getOrderCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should add limit sell order to asks")
        void shouldAddSellOrderToAsks() {
            Order order = createLimitOrder(1L, Order.Side.SELL, 100, "150.00");

            List<OrderBook.MatchResult> results = orderBook.addOrder(order);

            assertThat(results).isEmpty();
            assertThat(orderBook.getBestAsk()).isEqualByComparingTo("150.00");
            assertThat(orderBook.getAskDepth(1)).isEqualTo(100);
            assertThat(orderBook.getOrderCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should maintain price-time priority for bids")
        void shouldMaintainPriceTimePriorityForBids() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.BUY, 100, "150.00"));
            orderBook.addOrder(createLimitOrder(2L, Order.Side.BUY, 100, "151.00")); // Higher price
            orderBook.addOrder(createLimitOrder(3L, Order.Side.BUY, 100, "150.00")); // Same price, later

            // Best bid should be highest price
            assertThat(orderBook.getBestBid()).isEqualByComparingTo("151.00");
            assertThat(orderBook.getBidLevelCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should maintain price-time priority for asks")
        void shouldMaintainPriceTimePriorityForAsks() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.SELL, 100, "150.00"));
            orderBook.addOrder(createLimitOrder(2L, Order.Side.SELL, 100, "149.00")); // Lower price
            orderBook.addOrder(createLimitOrder(3L, Order.Side.SELL, 100, "150.00")); // Same price, later

            // Best ask should be lowest price
            assertThat(orderBook.getBestAsk()).isEqualByComparingTo("149.00");
            assertThat(orderBook.getAskLevelCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Order Matching Tests")
    class OrderMatchingTests {

        @Test
        @DisplayName("Should fully match crossing orders")
        void shouldFullyMatchCrossingOrders() {
            // Resting sell order
            orderBook.addOrder(createLimitOrder(1L, Order.Side.SELL, 100, "150.00"));

            // Incoming buy order crosses
            Order buyOrder = createLimitOrder(2L, Order.Side.BUY, 100, "150.00");
            List<OrderBook.MatchResult> results = orderBook.addOrder(buyOrder);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getQuantity()).isEqualTo(100);
            assertThat(results.get(0).getPrice()).isEqualByComparingTo("150.00");
            assertThat(orderBook.getOrderCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should partially match when incoming order is larger")
        void shouldPartiallyMatchLargerIncomingOrder() {
            // Resting sell order
            orderBook.addOrder(createLimitOrder(1L, Order.Side.SELL, 50, "150.00"));

            // Larger incoming buy order
            Order buyOrder = createLimitOrder(2L, Order.Side.BUY, 100, "150.00");
            List<OrderBook.MatchResult> results = orderBook.addOrder(buyOrder);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getQuantity()).isEqualTo(50);

            // Remaining 50 should be on bids
            assertThat(orderBook.getBestBid()).isEqualByComparingTo("150.00");
            assertThat(orderBook.getBidDepth(1)).isEqualTo(50);
        }

        @Test
        @DisplayName("Should partially match when resting order is larger")
        void shouldPartiallyMatchLargerRestingOrder() {
            // Larger resting sell order
            orderBook.addOrder(createLimitOrder(1L, Order.Side.SELL, 100, "150.00"));

            // Smaller incoming buy order
            Order buyOrder = createLimitOrder(2L, Order.Side.BUY, 50, "150.00");
            List<OrderBook.MatchResult> results = orderBook.addOrder(buyOrder);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getQuantity()).isEqualTo(50);

            // Remaining 50 should stay on asks
            assertThat(orderBook.getBestAsk()).isEqualByComparingTo("150.00");
            assertThat(orderBook.getAskDepth(1)).isEqualTo(50);
        }

        @Test
        @DisplayName("Should match multiple price levels")
        void shouldMatchMultiplePriceLevels() {
            // Multiple sell orders at different prices
            orderBook.addOrder(createLimitOrder(1L, Order.Side.SELL, 50, "150.00"));
            orderBook.addOrder(createLimitOrder(2L, Order.Side.SELL, 50, "151.00"));
            orderBook.addOrder(createLimitOrder(3L, Order.Side.SELL, 50, "152.00"));

            // Large aggressive buy
            Order buyOrder = createLimitOrder(4L, Order.Side.BUY, 120, "152.00");
            List<OrderBook.MatchResult> results = orderBook.addOrder(buyOrder);

            // Should match first two levels completely, third partially
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getPrice()).isEqualByComparingTo("150.00");
            assertThat(results.get(0).getQuantity()).isEqualTo(50);
            assertThat(results.get(1).getPrice()).isEqualByComparingTo("151.00");
            assertThat(results.get(1).getQuantity()).isEqualTo(50);
            assertThat(results.get(2).getPrice()).isEqualByComparingTo("152.00");
            assertThat(results.get(2).getQuantity()).isEqualTo(20);

            // 30 should remain at 152.00
            assertThat(orderBook.getBestAsk()).isEqualByComparingTo("152.00");
            assertThat(orderBook.getAskDepth(1)).isEqualTo(30);
        }

        @Test
        @DisplayName("Should not match non-crossing orders")
        void shouldNotMatchNonCrossingOrders() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.SELL, 100, "151.00"));
            orderBook.addOrder(createLimitOrder(2L, Order.Side.BUY, 100, "150.00"));

            // Both orders should rest in the book
            assertThat(orderBook.getBestBid()).isEqualByComparingTo("150.00");
            assertThat(orderBook.getBestAsk()).isEqualByComparingTo("151.00");
            assertThat(orderBook.getSpread()).isEqualByComparingTo("1.00");
            assertThat(orderBook.getOrderCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should track aggressor side correctly")
        void shouldTrackAggressorSide() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.SELL, 100, "150.00"));

            Order buyOrder = createLimitOrder(2L, Order.Side.BUY, 50, "150.00");
            List<OrderBook.MatchResult> results = orderBook.addOrder(buyOrder);

            assertThat(results.get(0).getAggressorSide()).isEqualTo(Order.Side.BUY);
        }
    }

    @Nested
    @DisplayName("Market Order Tests")
    class MarketOrderTests {

        @Test
        @DisplayName("Should match market buy against resting asks")
        void shouldMatchMarketBuyAgainstAsks() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.SELL, 100, "150.00"));

            Order marketBuy = createMarketOrder(2L, Order.Side.BUY, 50);
            List<OrderBook.MatchResult> results = orderBook.addOrder(marketBuy);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getPrice()).isEqualByComparingTo("150.00");
            assertThat(results.get(0).getQuantity()).isEqualTo(50);
        }

        @Test
        @DisplayName("Should match market sell against resting bids")
        void shouldMatchMarketSellAgainstBids() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.BUY, 100, "150.00"));

            Order marketSell = createMarketOrder(2L, Order.Side.SELL, 50);
            List<OrderBook.MatchResult> results = orderBook.addOrder(marketSell);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getPrice()).isEqualByComparingTo("150.00");
            assertThat(results.get(0).getQuantity()).isEqualTo(50);
        }

        @Test
        @DisplayName("Market order should not add to book")
        void marketOrderShouldNotAddToBook() {
            // No resting orders - market order has nothing to match
            Order marketBuy = createMarketOrder(1L, Order.Side.BUY, 50);
            List<OrderBook.MatchResult> results = orderBook.addOrder(marketBuy);

            assertThat(results).isEmpty();
            assertThat(orderBook.getOrderCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Cancel Order Tests")
    class CancelOrderTests {

        @Test
        @DisplayName("Should cancel existing order")
        void shouldCancelExistingOrder() {
            Order order = createLimitOrder(1L, Order.Side.BUY, 100, "150.00");
            orderBook.addOrder(order);

            boolean cancelled = orderBook.cancelOrder(1L);

            assertThat(cancelled).isTrue();
            assertThat(orderBook.getOrderCount()).isEqualTo(0);
            assertThat(orderBook.getBestBid()).isNull();
        }

        @Test
        @DisplayName("Should return false for non-existent order")
        void shouldReturnFalseForNonExistentOrder() {
            boolean cancelled = orderBook.cancelOrder(999L);

            assertThat(cancelled).isFalse();
        }

        @Test
        @DisplayName("Should remove price level when last order cancelled")
        void shouldRemovePriceLevelWhenLastOrderCancelled() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.BUY, 100, "150.00"));
            orderBook.addOrder(createLimitOrder(2L, Order.Side.BUY, 100, "151.00"));

            orderBook.cancelOrder(2L);

            assertThat(orderBook.getBestBid()).isEqualByComparingTo("150.00");
            assertThat(orderBook.getBidLevelCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should track trade statistics")
        void shouldTrackTradeStatistics() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.SELL, 100, "150.00"));
            orderBook.addOrder(createLimitOrder(2L, Order.Side.BUY, 100, "150.00"));

            assertThat(orderBook.getLastTradePrice()).isEqualByComparingTo("150.00");
            assertThat(orderBook.getLastTradeQuantity()).isEqualTo(100);
            assertThat(orderBook.getTotalVolume()).isEqualTo(100);
            assertThat(orderBook.getTotalTrades()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should calculate spread correctly")
        void shouldCalculateSpread() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.BUY, 100, "149.50"));
            orderBook.addOrder(createLimitOrder(2L, Order.Side.SELL, 100, "150.00"));

            assertThat(orderBook.getSpread()).isEqualByComparingTo("0.50");
        }

        @Test
        @DisplayName("Should return null spread when book is empty on one side")
        void shouldReturnNullSpreadWhenOneSideEmpty() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.BUY, 100, "150.00"));

            assertThat(orderBook.getSpread()).isNull();
        }
    }

    @Nested
    @DisplayName("Level 2 Market Data Tests")
    class Level2Tests {

        @Test
        @DisplayName("Should return correct bid depth levels")
        void shouldReturnCorrectBidDepthLevels() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.BUY, 100, "150.00"));
            orderBook.addOrder(createLimitOrder(2L, Order.Side.BUY, 150, "149.50"));
            orderBook.addOrder(createLimitOrder(3L, Order.Side.BUY, 200, "149.00"));

            List<OrderBook.PriceLevelData> bids = orderBook.getBids(3);

            assertThat(bids).hasSize(3);
            assertThat(bids.get(0).getPrice()).isEqualByComparingTo("150.00");
            assertThat(bids.get(0).getQuantity()).isEqualTo(100);
            assertThat(bids.get(1).getPrice()).isEqualByComparingTo("149.50");
            assertThat(bids.get(2).getPrice()).isEqualByComparingTo("149.00");
        }

        @Test
        @DisplayName("Should return correct ask depth levels")
        void shouldReturnCorrectAskDepthLevels() {
            orderBook.addOrder(createLimitOrder(1L, Order.Side.SELL, 100, "150.00"));
            orderBook.addOrder(createLimitOrder(2L, Order.Side.SELL, 150, "150.50"));
            orderBook.addOrder(createLimitOrder(3L, Order.Side.SELL, 200, "151.00"));

            List<OrderBook.PriceLevelData> asks = orderBook.getAsks(3);

            assertThat(asks).hasSize(3);
            assertThat(asks.get(0).getPrice()).isEqualByComparingTo("150.00");
            assertThat(asks.get(1).getPrice()).isEqualByComparingTo("150.50");
            assertThat(asks.get(2).getPrice()).isEqualByComparingTo("151.00");
        }
    }

    // Helper methods
    private Order createLimitOrder(long orderId, Order.Side side, long quantity, String price) {
        return Order.builder()
                .orderId(orderId)
                .clientId(1L)
                .symbol(SYMBOL)
                .side(side)
                .orderType(Order.OrderType.LIMIT)
                .quantity(quantity)
                .remainingQuantity(quantity)
                .filledQuantity(0L)
                .price(new BigDecimal(price))
                .status(Order.OrderStatus.NEW)
                .build();
    }

    private Order createMarketOrder(long orderId, Order.Side side, long quantity) {
        return Order.builder()
                .orderId(orderId)
                .clientId(1L)
                .symbol(SYMBOL)
                .side(side)
                .orderType(Order.OrderType.MARKET)
                .quantity(quantity)
                .remainingQuantity(quantity)
                .filledQuantity(0L)
                .status(Order.OrderStatus.NEW)
                .build();
    }
}
