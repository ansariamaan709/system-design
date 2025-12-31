package com.stockexchange.controller;

import com.stockexchange.dto.OrderRequest;
import com.stockexchange.dto.OrderResponse;
import com.stockexchange.entity.Order;
import com.stockexchange.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for order management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place a new order", description = "Submit a new order to the exchange")
    @ApiResponse(responseCode = "201", description = "Order submitted successfully")
    @ApiResponse(responseCode = "400", description = "Invalid order request")
    @ApiResponse(responseCode = "403", description = "Account not authorized to trade")
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestHeader("X-Client-Id") Long clientId,
            @RequestHeader("X-Account-Id") Long accountId,
            @Valid @RequestBody OrderRequest request) {

        log.info("[API] Place order: {} {} {} @ {}",
                request.getSide(), request.getQuantity(), request.getSymbol(), request.getPrice());

        OrderResponse response = orderService.submitOrder(clientId, accountId, request);

        HttpStatus status = response.getStatus() == Order.OrderStatus.REJECTED ? HttpStatus.BAD_REQUEST
                : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }

    @DeleteMapping("/{orderId}")
    @Operation(summary = "Cancel an order", description = "Cancel an existing order by ID")
    @ApiResponse(responseCode = "200", description = "Order cancelled successfully")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "400", description = "Order cannot be cancelled")
    public ResponseEntity<OrderResponse> cancelOrder(
            @RequestHeader("X-Client-Id") Long clientId,
            @PathVariable Long orderId) {

        log.info("[API] Cancel order: {}", orderId);
        OrderResponse response = orderService.cancelOrder(clientId, orderId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    @Operation(summary = "Cancel all orders", description = "Cancel all active orders for the client")
    @ApiResponse(responseCode = "200", description = "Orders cancelled successfully")
    public ResponseEntity<Map<String, Integer>> cancelAllOrders(
            @RequestHeader("X-Client-Id") Long clientId,
            @RequestParam(required = false) String symbol) {

        log.info("[API] Cancel all orders for client {} (symbol: {})", clientId, symbol);
        int cancelled = orderService.cancelAllOrders(clientId, symbol);
        return ResponseEntity.ok(Map.of("cancelled", cancelled));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID", description = "Retrieve order details by order ID")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<OrderResponse> getOrder(
            @RequestHeader("X-Client-Id") Long clientId,
            @PathVariable Long orderId) {

        OrderResponse response = orderService.getOrder(clientId, orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/client-order-id/{clientOrderId}")
    @Operation(summary = "Get order by client order ID")
    public ResponseEntity<OrderResponse> getOrderByClientOrderId(
            @RequestHeader("X-Client-Id") Long clientId,
            @PathVariable String clientOrderId) {

        OrderResponse response = orderService.getOrderByClientOrderId(clientId, clientOrderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get orders", description = "List orders with optional filters")
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @RequestHeader("X-Client-Id") Long clientId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(required = false) Order.Side side,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Page<OrderResponse> orders = orderService.getOrders(
                clientId, symbol, status, side, PageRequest.of(page, size, sort));
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active orders", description = "List all active (open) orders")
    public ResponseEntity<List<OrderResponse>> getActiveOrders(
            @RequestHeader("X-Client-Id") Long clientId) {

        List<OrderResponse> orders = orderService.getActiveOrders(clientId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/count")
    @Operation(summary = "Get active order count")
    public ResponseEntity<Map<String, Integer>> getActiveOrderCount(
            @RequestHeader("X-Client-Id") Long clientId) {

        List<OrderResponse> orders = orderService.getActiveOrders(clientId);
        return ResponseEntity.ok(Map.of("count", orders.size()));
    }
}
