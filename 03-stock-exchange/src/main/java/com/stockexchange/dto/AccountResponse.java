package com.stockexchange.dto;

import com.stockexchange.entity.Account;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for account information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private Long accountId;
    private Long clientId;
    private String accountNumber;
    private Account.AccountType accountType;
    private String currency;
    private Account.AccountStatus status;

    // Balances
    private BigDecimal cashBalance;
    private BigDecimal buyingPower;
    private BigDecimal marginUsed;
    private BigDecimal marginAvailable;
    private BigDecimal equity;

    // P&L
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private BigDecimal totalPnl;

    // Risk Metrics
    private BigDecimal dailyLossLimit;
    private BigDecimal dailyLossUsed;
    private Long maxPositionSize;
    private BigDecimal maxOrderValue;

    // Trading Limits
    private Boolean canTrade;
    private Boolean marginEnabled;
    private Integer rateLimitPerSecond;

    private LocalDateTime lastActivityAt;
    private LocalDateTime createdAt;

    public static AccountResponse fromAccount(Account account) {
        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .clientId(account.getClientId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .cashBalance(account.getCashBalance())
                .buyingPower(account.getBuyingPower())
                .marginUsed(account.getMarginUsed())
                .marginAvailable(account.getMarginAvailable())
                .equity(account.getEquity())
                .realizedPnl(account.getRealizedPnl())
                .unrealizedPnl(account.getUnrealizedPnl())
                .totalPnl(account.getRealizedPnl() != null && account.getUnrealizedPnl() != null
                        ? account.getRealizedPnl().add(account.getUnrealizedPnl())
                        : null)
                .dailyLossLimit(account.getDailyLossLimit())
                .maxPositionSize(account.getMaxPositionSize())
                .maxOrderValue(account.getMaxOrderValue())
                .canTrade(account.isCanTrade())
                .marginEnabled(account.isMarginEnabled())
                .rateLimitPerSecond(account.getRateLimitPerSecond())
                .lastActivityAt(account.getLastActivityAt())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
