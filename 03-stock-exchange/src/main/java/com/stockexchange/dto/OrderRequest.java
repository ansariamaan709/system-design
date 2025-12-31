package com.stockexchange.dto;

import com.stockexchange.entity.Order;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request DTO for placing a new order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotBlank(message = "Symbol is required")
    @Size(min = 1, max = 10, message = "Symbol must be 1-10 characters")
    private String symbol;

    @NotNull(message = "Side is required")
    private Order.Side side;

    @NotNull(message = "Order type is required")
    private Order.OrderType orderType;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 10_000_000, message = "Quantity cannot exceed 10,000,000")
    private Long quantity;

    @DecimalMin(value = "0.0001", message = "Price must be positive")
    @DecimalMax(value = "1000000", message = "Price cannot exceed 1,000,000")
    private BigDecimal price; // Required for LIMIT, STOP_LIMIT orders

    @DecimalMin(value = "0.0001", message = "Stop price must be positive")
    private BigDecimal stopPrice; // Required for STOP, STOP_LIMIT orders

    private Order.TimeInForce timeInForce; // Default: DAY

    @Size(max = 100, message = "Client order ID cannot exceed 100 characters")
    private String clientOrderId;

    private LocalDateTime expireTime; // For GTD orders

    @Min(value = 0, message = "Display quantity cannot be negative")
    private Long displayQuantity; // For iceberg orders

    private Boolean postOnly; // For maker-only orders

    private Boolean reduceOnly; // Only reduce position, don't increase
}
