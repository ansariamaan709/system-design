package com.stockexchange.controller;

import com.stockexchange.dto.AccountResponse;
import com.stockexchange.dto.PositionResponse;
import com.stockexchange.dto.TradeResponse;
import com.stockexchange.entity.Account;
import com.stockexchange.entity.Position;
import com.stockexchange.repository.AccountRepository;
import com.stockexchange.repository.PositionRepository;
import com.stockexchange.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST API for account and portfolio management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Account & Portfolio", description = "Account and portfolio management")
public class AccountController {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final TradeService tradeService;

    // ==================== Account Endpoints ====================

    @GetMapping("/accounts")
    @Operation(summary = "Get accounts", description = "Get all accounts for a client")
    public ResponseEntity<List<AccountResponse>> getAccounts(
            @RequestHeader("X-Client-Id") Long clientId) {

        List<Account> accounts = accountRepository.findByClientId(clientId);
        List<AccountResponse> response = accounts.stream()
                .map(AccountResponse::fromAccount)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Get account", description = "Get account details by ID")
    public ResponseEntity<AccountResponse> getAccount(
            @RequestHeader("X-Client-Id") Long clientId,
            @PathVariable Long accountId) {

        Account account = accountRepository.findById(accountId)
                .filter(a -> a.getClientId().equals(clientId))
                .orElse(null);

        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(AccountResponse.fromAccount(account));
    }

    @GetMapping("/accounts/{accountId}/balance")
    @Operation(summary = "Get account balance", description = "Get account balance summary")
    public ResponseEntity<Map<String, BigDecimal>> getAccountBalance(
            @RequestHeader("X-Client-Id") Long clientId,
            @PathVariable Long accountId) {

        Account account = accountRepository.findById(accountId)
                .filter(a -> a.getClientId().equals(clientId))
                .orElse(null);

        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "cashBalance", account.getCashBalance() != null ? account.getCashBalance() : BigDecimal.ZERO,
                "buyingPower", account.getBuyingPower() != null ? account.getBuyingPower() : BigDecimal.ZERO,
                "equity", account.getEquity() != null ? account.getEquity() : BigDecimal.ZERO,
                "marginUsed", account.getMarginUsed() != null ? account.getMarginUsed() : BigDecimal.ZERO,
                "marginAvailable",
                account.getMarginAvailable() != null ? account.getMarginAvailable() : BigDecimal.ZERO,
                "unrealizedPnl", account.getUnrealizedPnl() != null ? account.getUnrealizedPnl() : BigDecimal.ZERO,
                "realizedPnl", account.getRealizedPnl() != null ? account.getRealizedPnl() : BigDecimal.ZERO));
    }

    // ==================== Position Endpoints ====================

    @GetMapping("/positions")
    @Operation(summary = "Get positions", description = "Get all positions for an account")
    public ResponseEntity<List<PositionResponse>> getPositions(
            @RequestHeader("X-Account-Id") Long accountId) {

        List<Position> positions = positionRepository.findOpenPositionsByAccountId(accountId);
        List<PositionResponse> response = positions.stream()
                .map(PositionResponse::fromPosition)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/positions/{symbol}")
    @Operation(summary = "Get position", description = "Get position for a specific symbol")
    public ResponseEntity<PositionResponse> getPosition(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String symbol) {

        Position position = positionRepository.findByAccountIdAndSymbol(accountId, symbol.toUpperCase())
                .orElse(null);

        if (position == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(PositionResponse.fromPosition(position));
    }

    @GetMapping("/positions/summary")
    @Operation(summary = "Get position summary", description = "Get portfolio summary")
    public ResponseEntity<Map<String, Object>> getPositionSummary(
            @RequestHeader("X-Account-Id") Long accountId) {

        List<Position> positions = positionRepository.findOpenPositionsByAccountId(accountId);

        BigDecimal totalValue = positionRepository.getPortfolioValue(accountId);
        BigDecimal totalPnl = positionRepository.getTotalUnrealizedPnl(accountId);

        return ResponseEntity.ok(Map.of(
                "positionCount", positions.size(),
                "totalValue", totalValue != null ? totalValue : BigDecimal.ZERO,
                "unrealizedPnl", totalPnl != null ? totalPnl : BigDecimal.ZERO,
                "positions", positions.stream().map(PositionResponse::fromPosition).toList()));
    }

    // ==================== Trade History Endpoints ====================

    @GetMapping("/trades")
    @Operation(summary = "Get trades", description = "Get trade history for an account")
    public ResponseEntity<Page<TradeResponse>> getTrades(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<TradeResponse> trades = tradeService.getTradesForAccount(
                accountId, PageRequest.of(page, size, Sort.by("executedAt").descending()));
        return ResponseEntity.ok(trades);
    }

    @GetMapping("/trades/order/{orderId}")
    @Operation(summary = "Get trades for order", description = "Get all trades for a specific order")
    public ResponseEntity<List<TradeResponse>> getTradesForOrder(
            @PathVariable Long orderId) {

        List<TradeResponse> trades = tradeService.getTradesForOrder(orderId);
        return ResponseEntity.ok(trades);
    }
}
