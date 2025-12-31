package com.stockexchange.dto;

import com.stockexchange.entity.Trade;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for trade information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeResponse {

    private Long tradeId;
    private String symbol;

    private BigDecimal price;
    private Long quantity;
    private BigDecimal value;

    private Long buyOrderId;
    private Long sellOrderId;

    private Long buyerId;
    private Long sellerId;

    private String aggressorSide;

    private LocalDateTime executedAt;
    private LocalDate settlementDate;
    private String settlementStatus;

    private BigDecimal commission;
    private BigDecimal fee;

    public static TradeResponse fromTrade(Trade trade) {
        return TradeResponse.builder()
                .tradeId(trade.getTradeId())
                .symbol(trade.getSymbol())
                .price(trade.getPrice())
                .quantity(trade.getQuantity())
                .value(trade.getValue())
                .buyOrderId(trade.getBuyOrderId())
                .sellOrderId(trade.getSellOrderId())
                .buyerId(trade.getBuyerAccountId())
                .sellerId(trade.getSellerAccountId())
                .aggressorSide(trade.getAggressorSide())
                .executedAt(trade.getExecutedAt())
                .settlementDate(trade.getSettlementDate())
                .settlementStatus(trade.getSettlementStatus() != null ? trade.getSettlementStatus().name() : null)
                .commission(
                        trade.getBuyerCommission() != null ? trade.getBuyerCommission().add(trade.getSellerCommission())
                                : null)
                .build();
    }
}
