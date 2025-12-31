package com.stockexchange.service;

import com.stockexchange.dto.OrderRequest;
import com.stockexchange.dto.OrderResponse;
import com.stockexchange.engine.MatchingEngine;
import com.stockexchange.engine.RiskManager;
import com.stockexchange.entity.Account;
import com.stockexchange.entity.Instrument;
import com.stockexchange.entity.Order;
import com.stockexchange.entity.Position;
import com.stockexchange.exception.ExchangeException;
import com.stockexchange.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private AccountRepository accountRepository;

        @Mock
        private InstrumentRepository instrumentRepository;

        @Mock
        private PositionRepository positionRepository;

        @Mock
        private MatchingEngine matchingEngine;

        @Mock
        private RiskManager riskManager;

        @Mock
        private MarketDataService marketDataService;

        @InjectMocks
        private OrderService orderService;

        private OrderRequest validOrderRequest;
        private Account testAccount;
        private Instrument testInstrument;

        @BeforeEach
        void setUp() {
                validOrderRequest = OrderRequest.builder()
                                .symbol("AAPL")
                                .side(Order.Side.BUY)
                                .orderType(Order.OrderType.LIMIT)
                                .quantity(100L)
                                .price(new BigDecimal("150.00"))
                                .build();

                testAccount = Account.builder()
                                .accountId(1L)
                                .clientId(1L)
                                .canTrade(true)
                                .buyingPower(new BigDecimal("100000"))
                                .maxPositionSize(100000L)
                                .maxOrderValue(new BigDecimal("1000000"))
                                .rateLimitPerSecond(100)
                                .build();

                testInstrument = Instrument.builder()
                                .symbol("AAPL")
                                .name("Apple Inc.")
                                .type(Instrument.InstrumentType.STOCK)
                                .lotSize(1)
                                .tickSize(new BigDecimal("0.01"))
                                .tradeable(true)
                                .lastPrice(new BigDecimal("150.00"))
                                .tradingStatus(Instrument.TradingStatus.OPEN)
                                .build();
        }

        @Nested
        @DisplayName("Submit Order Tests")
        class SubmitOrderTests {

                @Test
                @DisplayName("Should submit valid order successfully")
                void shouldSubmitValidOrderSuccessfully() {
                        // Arrange
                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));
                        when(accountRepository.findById(1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL"))
                                        .thenReturn(Optional.empty());
                        when(marketDataService.getLastPrice("AAPL"))
                                        .thenReturn(new BigDecimal("150.00"));
                        when(riskManager.checkOrder(any(), any(), any(), any()))
                                        .thenReturn(RiskManager.RiskCheckResult.approved());
                        when(orderRepository.save(any(Order.class)))
                                        .thenAnswer(inv -> {
                                                Order o = inv.getArgument(0);
                                                o.setOrderId(1L);
                                                return o;
                                        });
                        when(matchingEngine.submitOrder(any())).thenReturn(1L);

                        // Act
                        OrderResponse response = orderService.submitOrder(1L, 1L, validOrderRequest);

                        // Assert
                        assertThat(response).isNotNull();
                        assertThat(response.getOrderId()).isEqualTo(1L);
                        assertThat(response.getSymbol()).isEqualTo("AAPL");

                        verify(orderRepository).save(any(Order.class));
                        verify(matchingEngine).submitOrder(any(Order.class));
                }

                @Test
                @DisplayName("Should reject order when instrument not found")
                void shouldRejectOrderWhenInstrumentNotFound() {
                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> orderService.submitOrder(1L, 1L, validOrderRequest))
                                        .isInstanceOf(ExchangeException.class)
                                        .hasMessageContaining("Unknown symbol");
                }

                @Test
                @DisplayName("Should reject order when account not found")
                void shouldRejectOrderWhenAccountNotFound() {
                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));
                        when(accountRepository.findById(1L))
                                        .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> orderService.submitOrder(1L, 1L, validOrderRequest))
                                        .isInstanceOf(ExchangeException.class)
                                        .hasMessageContaining("Unknown account");
                }

                @Test
                @DisplayName("Should reject order when account cannot trade")
                void shouldRejectOrderWhenAccountCannotTrade() {
                        testAccount.setCanTrade(false);

                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));
                        when(accountRepository.findById(1L))
                                        .thenReturn(Optional.of(testAccount));

                        assertThatThrownBy(() -> orderService.submitOrder(1L, 1L, validOrderRequest))
                                        .isInstanceOf(ExchangeException.class)
                                        .hasMessageContaining("not enabled for trading");
                }

                @Test
                @DisplayName("Should reject order when trading is halted")
                void shouldRejectOrderWhenTradingHalted() {
                        testInstrument.setTradingStatus(Instrument.TradingStatus.HALTED);

                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));
                        when(accountRepository.findById(1L))
                                        .thenReturn(Optional.of(testAccount));

                        assertThatThrownBy(() -> orderService.submitOrder(1L, 1L, validOrderRequest))
                                        .isInstanceOf(ExchangeException.class)
                                        .hasMessageContaining("halted");
                }

                @Test
                @DisplayName("Should reject order when risk check fails")
                void shouldRejectOrderWhenRiskCheckFails() {
                        when(instrumentRepository.findBySymbol("AAPL"))
                                        .thenReturn(Optional.of(testInstrument));
                        when(accountRepository.findById(1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL"))
                                        .thenReturn(Optional.empty());
                        when(marketDataService.getLastPrice("AAPL"))
                                        .thenReturn(new BigDecimal("150.00"));
                        when(riskManager.checkOrder(any(), any(), any(), any()))
                                        .thenReturn(RiskManager.RiskCheckResult.rejected(
                                                        "INSUFFICIENT_BUYING_POWER", "Not enough funds"));
                        when(orderRepository.save(any(Order.class)))
                                        .thenAnswer(inv -> {
                                                Order o = inv.getArgument(0);
                                                o.setOrderId(1L);
                                                return o;
                                        });

                        OrderResponse response = orderService.submitOrder(1L, 1L, validOrderRequest);

                        assertThat(response.getStatus()).isEqualTo(Order.OrderStatus.REJECTED);
                        assertThat(response.getRejectReason()).contains("Not enough funds");
                }
        }

        @Nested
        @DisplayName("Cancel Order Tests")
        class CancelOrderTests {

                @Test
                @DisplayName("Should cancel existing order")
                void shouldCancelExistingOrder() {
                        Order order = Order.builder()
                                        .orderId(1L)
                                        .clientId(1L)
                                        .accountId(1L)
                                        .symbol("AAPL")
                                        .status(Order.OrderStatus.NEW)
                                        .side(Order.Side.BUY)
                                        .quantity(100L)
                                        .remainingQuantity(100L)
                                        .filledQuantity(0L)
                                        .price(new BigDecimal("150.00"))
                                        .build();

                        when(orderRepository.findByOrderIdAndClientId(1L, 1L)).thenReturn(Optional.of(order));
                        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
                        when(orderRepository.save(any())).thenReturn(order);

                        OrderResponse response = orderService.cancelOrder(1L, 1L);

                        verify(matchingEngine).cancelOrder(eq("AAPL"), eq(1L), eq(1L));
                }

                @Test
                @DisplayName("Should throw when cancelling non-existent order")
                void shouldThrowWhenCancellingNonExistentOrder() {
                        when(orderRepository.findByOrderIdAndClientId(999L, 1L)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> orderService.cancelOrder(1L, 999L))
                                        .isInstanceOf(ExchangeException.class)
                                        .hasMessageContaining("Order not found");
                }

                @Test
                @DisplayName("Should throw when cancelling already filled order")
                void shouldThrowWhenCancellingFilledOrder() {
                        Order order = Order.builder()
                                        .orderId(1L)
                                        .clientId(1L)
                                        .symbol("AAPL")
                                        .status(Order.OrderStatus.FILLED)
                                        .build();

                        when(orderRepository.findByOrderIdAndClientId(1L, 1L)).thenReturn(Optional.of(order));

                        assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L))
                                        .isInstanceOf(ExchangeException.class)
                                        .hasMessageContaining("cannot be cancelled");
                }
        }

        @Nested
        @DisplayName("Get Order Tests")
        class GetOrderTests {

                @Test
                @DisplayName("Should return order when exists")
                void shouldReturnOrderWhenExists() {
                        Order order = Order.builder()
                                        .orderId(1L)
                                        .clientId(1L)
                                        .symbol("AAPL")
                                        .side(Order.Side.BUY)
                                        .orderType(Order.OrderType.LIMIT)
                                        .quantity(100L)
                                        .filledQuantity(0L)
                                        .remainingQuantity(100L)
                                        .price(new BigDecimal("150.00"))
                                        .status(Order.OrderStatus.NEW)
                                        .createdAt(LocalDateTime.now())
                                        .build();

                        when(orderRepository.findByOrderIdAndClientId(1L, 1L)).thenReturn(Optional.of(order));

                        OrderResponse response = orderService.getOrder(1L, 1L);

                        assertThat(response.getOrderId()).isEqualTo(1L);
                        assertThat(response.getSymbol()).isEqualTo("AAPL");
                }

                @Test
                @DisplayName("Should throw when order not found")
                void shouldThrowWhenOrderNotFound() {
                        when(orderRepository.findByOrderIdAndClientId(999L, 1L)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> orderService.getOrder(1L, 999L))
                                        .isInstanceOf(ExchangeException.class)
                                        .hasMessageContaining("Order not found");
                }
        }
}
