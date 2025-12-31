package com.stockexchange.dto;

import com.stockexchange.entity.Order;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Response DTO for order information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long orderId;
    private String clientOrderId;
    private String symbol;
    private Order.Side side;
    private Order.OrderType orderType;
    private Order.TimeInForce timeInForce;
    private Order.OrderStatus status;

    private Long quantity;
    private Long filledQuantity;
    private Long remainingQuantity;

    private BigDecimal price;
    private BigDecimal stopPrice;
    private BigDecimal averagePrice;

    private BigDecimal executedValue;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expireTime;

    private String rejectReason;

    // Execution statistics
    private Integer fillCount;
    private Long latencyMicros;

    public static OrderResponse fromOrder(Order order) {
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .clientOrderId(order.getClientOrderId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .orderType(order.getOrderType())
                .timeInForce(order.getTimeInForce())
                .status(order.getStatus())
                .quantity(order.getQuantity())
                .filledQuantity(order.getFilledQuantity())
                .remainingQuantity(order.getRemainingQuantity())
                .price(order.getPrice())
                .stopPrice(order.getStopPrice())
                .averagePrice(order.getAvgFillPrice())
                .executedValue(order.getPrice() != null && order.getFilledQuantity() != null
                        && order.getFilledQuantity() > 0 && order.getAvgFillPrice() != null
                                ? order.getAvgFillPrice().multiply(BigDecimal.valueOf(order.getFilledQuantity()))
                                : null)
                .createdAt(toLocalDateTime(order.getCreatedAt()))
                .updatedAt(toLocalDateTime(order.getUpdatedAt()))
                .rejectReason(order.getRejectReason())
                .build();
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null)
            return null;
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
