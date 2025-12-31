package com.stockexchange.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockexchange.dto.OrderRequest;
import com.stockexchange.dto.OrderResponse;
import com.stockexchange.entity.Order;
import com.stockexchange.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for OrderController.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private OrderService orderService;

        private static final Long TEST_CLIENT_ID = 1L;
        private static final Long TEST_ACCOUNT_ID = 1L;

        @Nested
        @DisplayName("POST /api/v1/orders - Place Order")
        class PlaceOrderTests {

                @Test
                @DisplayName("Should place valid limit buy order")
                void shouldPlaceValidLimitBuyOrder() throws Exception {
                        OrderRequest request = OrderRequest.builder()
                                        .symbol("AAPL")
                                        .side(Order.Side.BUY)
                                        .orderType(Order.OrderType.LIMIT)
                                        .quantity(100L)
                                        .price(new BigDecimal("150.00"))
                                        .build();

                        OrderResponse response = OrderResponse.builder()
                                        .orderId(1L)
                                        .clientOrderId("CLT-001")
                                        .symbol("AAPL")
                                        .side(Order.Side.BUY)
                                        .orderType(Order.OrderType.LIMIT)
                                        .quantity(100L)
                                        .price(new BigDecimal("150.00"))
                                        .status(Order.OrderStatus.NEW)
                                        .createdAt(LocalDateTime.now())
                                        .build();

                        when(orderService.submitOrder(eq(TEST_CLIENT_ID), eq(TEST_ACCOUNT_ID), any(OrderRequest.class)))
                                        .thenReturn(response);

                        mockMvc.perform(post("/api/v1/orders")
                                        .header("X-Client-Id", TEST_CLIENT_ID)
                                        .header("X-Account-Id", TEST_ACCOUNT_ID)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.orderId").value(1))
                                        .andExpect(jsonPath("$.symbol").value("AAPL"))
                                        .andExpect(jsonPath("$.side").value("BUY"))
                                        .andExpect(jsonPath("$.status").value("NEW"));
                }

                @Test
                @DisplayName("Should place valid market order")
                void shouldPlaceValidMarketOrder() throws Exception {
                        OrderRequest request = OrderRequest.builder()
                                        .symbol("AAPL")
                                        .side(Order.Side.BUY)
                                        .orderType(Order.OrderType.MARKET)
                                        .quantity(100L)
                                        .build();

                        OrderResponse response = OrderResponse.builder()
                                        .orderId(2L)
                                        .symbol("AAPL")
                                        .side(Order.Side.BUY)
                                        .orderType(Order.OrderType.MARKET)
                                        .quantity(100L)
                                        .status(Order.OrderStatus.NEW)
                                        .createdAt(LocalDateTime.now())
                                        .build();

                        when(orderService.submitOrder(eq(TEST_CLIENT_ID), eq(TEST_ACCOUNT_ID), any(OrderRequest.class)))
                                        .thenReturn(response);

                        mockMvc.perform(post("/api/v1/orders")
                                        .header("X-Client-Id", TEST_CLIENT_ID)
                                        .header("X-Account-Id", TEST_ACCOUNT_ID)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.orderType").value("MARKET"));
                }

                @Test
                @DisplayName("Should return bad request when order is rejected")
                void shouldReturnBadRequestWhenOrderRejected() throws Exception {
                        OrderRequest request = OrderRequest.builder()
                                        .symbol("AAPL")
                                        .side(Order.Side.BUY)
                                        .orderType(Order.OrderType.LIMIT)
                                        .quantity(100L)
                                        .price(new BigDecimal("150.00"))
                                        .build();

                        OrderResponse response = OrderResponse.builder()
                                        .orderId(1L)
                                        .symbol("AAPL")
                                        .status(Order.OrderStatus.REJECTED)
                                        .rejectReason("Insufficient buying power")
                                        .build();

                        when(orderService.submitOrder(eq(TEST_CLIENT_ID), eq(TEST_ACCOUNT_ID), any(OrderRequest.class)))
                                        .thenReturn(response);

                        mockMvc.perform(post("/api/v1/orders")
                                        .header("X-Client-Id", TEST_CLIENT_ID)
                                        .header("X-Account-Id", TEST_ACCOUNT_ID)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.status").value("REJECTED"));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/orders/{orderId} - Get Order")
        class GetOrderTests {

                @Test
                @DisplayName("Should return order when exists")
                void shouldReturnOrderWhenExists() throws Exception {
                        OrderResponse response = OrderResponse.builder()
                                        .orderId(1L)
                                        .symbol("AAPL")
                                        .side(Order.Side.BUY)
                                        .orderType(Order.OrderType.LIMIT)
                                        .quantity(100L)
                                        .price(new BigDecimal("150.00"))
                                        .status(Order.OrderStatus.NEW)
                                        .createdAt(LocalDateTime.now())
                                        .build();

                        when(orderService.getOrder(TEST_CLIENT_ID, 1L)).thenReturn(response);

                        mockMvc.perform(get("/api/v1/orders/1")
                                        .header("X-Client-Id", TEST_CLIENT_ID))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.orderId").value(1))
                                        .andExpect(jsonPath("$.symbol").value("AAPL"));
                }
        }

        @Nested
        @DisplayName("DELETE /api/v1/orders/{orderId} - Cancel Order")
        class CancelOrderTests {

                @Test
                @DisplayName("Should cancel order successfully")
                void shouldCancelOrderSuccessfully() throws Exception {
                        OrderResponse response = OrderResponse.builder()
                                        .orderId(1L)
                                        .symbol("AAPL")
                                        .status(Order.OrderStatus.CANCELLED)
                                        .build();

                        when(orderService.cancelOrder(TEST_CLIENT_ID, 1L)).thenReturn(response);

                        mockMvc.perform(delete("/api/v1/orders/1")
                                        .header("X-Client-Id", TEST_CLIENT_ID))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.status").value("CANCELLED"));
                }
        }

        @Nested
        @DisplayName("DELETE /api/v1/orders - Cancel All Orders")
        class CancelAllOrdersTests {

                @Test
                @DisplayName("Should cancel all orders for client")
                void shouldCancelAllOrdersForClient() throws Exception {
                        when(orderService.cancelAllOrders(TEST_CLIENT_ID, null)).thenReturn(5);

                        mockMvc.perform(delete("/api/v1/orders")
                                        .header("X-Client-Id", TEST_CLIENT_ID))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.cancelled").value(5));
                }

                @Test
                @DisplayName("Should cancel all orders for symbol")
                void shouldCancelAllOrdersForSymbol() throws Exception {
                        when(orderService.cancelAllOrders(TEST_CLIENT_ID, "AAPL")).thenReturn(3);

                        mockMvc.perform(delete("/api/v1/orders")
                                        .header("X-Client-Id", TEST_CLIENT_ID)
                                        .param("symbol", "AAPL"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.cancelled").value(3));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/orders - List Orders")
        class ListOrdersTests {

                @Test
                @DisplayName("Should list orders with pagination")
                void shouldListOrdersWithPagination() throws Exception {
                        List<OrderResponse> orders = Arrays.asList(
                                        OrderResponse.builder().orderId(1L).symbol("AAPL").status(Order.OrderStatus.NEW)
                                                        .build(),
                                        OrderResponse.builder().orderId(2L).symbol("AAPL")
                                                        .status(Order.OrderStatus.FILLED).build());

                        when(orderService.getOrders(eq(TEST_CLIENT_ID), any(), any(), any(), any(Pageable.class)))
                                        .thenReturn(new PageImpl<>(orders));

                        mockMvc.perform(get("/api/v1/orders")
                                        .header("X-Client-Id", TEST_CLIENT_ID)
                                        .param("symbol", "AAPL"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content").isArray())
                                        .andExpect(jsonPath("$.content.length()").value(2));
                }

                @Test
                @DisplayName("Should get active orders")
                void shouldGetActiveOrders() throws Exception {
                        List<OrderResponse> orders = Arrays.asList(
                                        OrderResponse.builder().orderId(1L).symbol("AAPL").status(Order.OrderStatus.NEW)
                                                        .build());

                        when(orderService.getActiveOrders(TEST_CLIENT_ID)).thenReturn(orders);

                        mockMvc.perform(get("/api/v1/orders/active")
                                        .header("X-Client-Id", TEST_CLIENT_ID))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$").isArray())
                                        .andExpect(jsonPath("$.length()").value(1));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/orders/client-order-id/{clientOrderId}")
        class GetOrderByClientOrderIdTests {

                @Test
                @DisplayName("Should return order by client order ID")
                void shouldReturnOrderByClientOrderId() throws Exception {
                        OrderResponse response = OrderResponse.builder()
                                        .orderId(1L)
                                        .clientOrderId("MY-ORDER-001")
                                        .symbol("AAPL")
                                        .status(Order.OrderStatus.NEW)
                                        .build();

                        when(orderService.getOrderByClientOrderId(TEST_CLIENT_ID, "MY-ORDER-001"))
                                        .thenReturn(response);

                        mockMvc.perform(get("/api/v1/orders/client-order-id/MY-ORDER-001")
                                        .header("X-Client-Id", TEST_CLIENT_ID))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.clientOrderId").value("MY-ORDER-001"));
                }
        }
}
