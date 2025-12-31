package com.stockexchange.dto;

import com.stockexchange.entity.Position;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for position information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponse {

    private Long positionId;
    private Long accountId;
    private String symbol;

    private Long quantity;
    private Position.PositionSide side;

    private BigDecimal averageCost;
    private BigDecimal marketPrice;
    private BigDecimal marketValue;
    private BigDecimal costBasis;

    // P&L
    private BigDecimal unrealizedPnl;
    private BigDecimal unrealizedPnlPercent;
    private BigDecimal realizedPnl;
    private BigDecimal todayPnl;

    // Weight
    private BigDecimal portfolioWeight;

    private LocalDateTime openedAt;
    private LocalDateTime lastUpdatedAt;

    public static PositionResponse fromPosition(Position position) {
        BigDecimal unrealizedPnlPercent = null;
        if (position.getCostBasis() != null &&
                position.getCostBasis().compareTo(BigDecimal.ZERO) != 0 &&
                position.getUnrealizedPnl() != null) {
            unrealizedPnlPercent = position.getUnrealizedPnl()
                    .divide(position.getCostBasis(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        return PositionResponse.builder()
                .positionId(position.getPositionId())
                .accountId(position.getAccountId())
                .symbol(position.getSymbol())
                .quantity(position.getQuantity())
                .side(position.getSide() != null ? position.getSide() : position.getPositionSide())
                .averageCost(position.getAverageCost() != null ? position.getAverageCost() : position.getAvgCost())
                .marketPrice(position.getMarketPrice() != null ? position.getMarketPrice() : position.getCurrentPrice())
                .marketValue(position.getMarketValue())
                .costBasis(position.getCostBasis())
                .unrealizedPnl(position.getUnrealizedPnl())
                .unrealizedPnlPercent(unrealizedPnlPercent)
                .realizedPnl(position.getRealizedPnl())
                .todayPnl(position.getTodayPnl() != null ? position.getTodayPnl() : position.getRealizedPnlToday())
                .openedAt(position.getOpenedAt())
                .lastUpdatedAt(position.getUpdatedAt())
                .build();
    }
}
