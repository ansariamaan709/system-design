package com.stockexchange.service;

import com.stockexchange.dto.TradeResponse;
import com.stockexchange.engine.MatchingEngine;
import com.stockexchange.engine.OrderBook;
import com.stockexchange.entity.Account;
import com.stockexchange.entity.Position;
import com.stockexchange.entity.Trade;
import com.stockexchange.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Trade execution and settlement service.
 * Handles trade persistence, position updates, P&L calculation, and settlement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService implements MatchingEngine.TradeListener {

    private final MatchingEngine matchingEngine;
    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final OrderService orderService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Commission rates
    private static final BigDecimal MAKER_COMMISSION_RATE = new BigDecimal("0.0001"); // 1 bp
    private static final BigDecimal TAKER_COMMISSION_RATE = new BigDecimal("0.0002"); // 2 bp

    @PostConstruct
    public void init() {
        // Register as trade listener
        matchingEngine.addTradeListener(this);
        log.info("[TRADE SERVICE] Registered as trade listener");
    }

    /**
     * Handle trade event from matching engine.
     */
    @Override
    @Transactional
    public void onTrade(OrderBook.MatchResult matchResult) {
        long startTime = System.nanoTime();

        try {
            // Create trade entity
            Trade trade = createTrade(matchResult);
            trade = tradeRepository.save(trade);

            // Update positions
            updatePositions(trade);

            // Update account balances
            updateAccountBalances(trade);

            // Update instrument statistics
            updateInstrumentStats(trade);

            // Notify order service
            orderService.onOrderFilled(matchResult);

            // Publish trade event to Kafka
            publishTradeEvent(trade);

            long latency = (System.nanoTime() - startTime) / 1000;
            log.info("[TRADE] Trade {} executed: {} {} @ {} ({}μs)",
                    trade.getTradeId(), trade.getSymbol(), trade.getQuantity(),
                    trade.getPrice(), latency);

        } catch (Exception e) {
            log.error("[TRADE] Error processing trade: {}", e.getMessage(), e);
            throw e;
        }
    }

    private Trade createTrade(OrderBook.MatchResult matchResult) {
        BigDecimal value = matchResult.getPrice().multiply(
                BigDecimal.valueOf(matchResult.getQuantity()));

        // Calculate commissions
        BigDecimal buyerCommission = value.multiply(
                matchResult.getAggressorSide() == com.stockexchange.entity.Order.Side.BUY ? TAKER_COMMISSION_RATE
                        : MAKER_COMMISSION_RATE);
        BigDecimal sellerCommission = value.multiply(
                matchResult.getAggressorSide() == com.stockexchange.entity.Order.Side.SELL ? TAKER_COMMISSION_RATE
                        : MAKER_COMMISSION_RATE);

        return Trade.builder()
                .symbol(matchResult.getSymbol())
                .price(matchResult.getPrice())
                .quantity(matchResult.getQuantity())
                .buyOrderId(matchResult.getBuyOrderId())
                .sellOrderId(matchResult.getSellOrderId())
                .buyerAccountId(matchResult.getBuyerId())
                .sellerAccountId(matchResult.getSellerId())
                .buyerId(matchResult.getBuyerId())
                .sellerId(matchResult.getSellerId())
                .aggressorSide(matchResult.getAggressorSide().name())
                .buyerCommission(buyerCommission)
                .sellerCommission(sellerCommission)
                .value(value)
                .tradeValue(value)
                .executedAt(LocalDateTime.now())
                .settlementDate(LocalDate.now().plusDays(2)) // T+2 settlement
                .settlementStatus(Trade.SettlementStatus.PENDING)
                .build();
    }

    private void updatePositions(Trade trade) {
        // Update buyer position (add)
        updatePosition(trade.getBuyerAccountId(), trade.getSymbol(),
                trade.getQuantity(), trade.getPrice());

        // Update seller position (subtract)
        updatePosition(trade.getSellerAccountId(), trade.getSymbol(),
                -trade.getQuantity(), trade.getPrice());
    }

    private void updatePosition(Long accountId, String symbol, long quantityDelta, BigDecimal price) {
        Position position = positionRepository.findByAccountIdAndSymbol(accountId, symbol)
                .orElseGet(() -> Position.builder()
                        .accountId(accountId)
                        .clientId(accountId) // Using accountId as clientId placeholder
                        .symbol(symbol)
                        .quantity(0L)
                        .avgCost(BigDecimal.ZERO)
                        .averageCost(BigDecimal.ZERO)
                        .costBasis(BigDecimal.ZERO)
                        .marketPrice(price)
                        .currentPrice(price)
                        .marketValue(BigDecimal.ZERO)
                        .unrealizedPnl(BigDecimal.ZERO)
                        .realizedPnl(BigDecimal.ZERO)
                        .todayPnl(BigDecimal.ZERO)
                        .realizedPnlToday(BigDecimal.ZERO)
                        .openedAt(LocalDateTime.now())
                        .build());

        BigDecimal avgCostBefore = calculateAverageCost(position);

        // Calculate realized P&L for closing positions
        BigDecimal realizedPnl = BigDecimal.ZERO;
        if ((position.getQuantity() > 0 && quantityDelta < 0) ||
                (position.getQuantity() < 0 && quantityDelta > 0)) {
            // Closing or reducing position
            long closingQty = Math.min(Math.abs(position.getQuantity()), Math.abs(quantityDelta));
            realizedPnl = price.subtract(avgCostBefore)
                    .multiply(BigDecimal.valueOf(closingQty));
            if (quantityDelta > 0) {
                realizedPnl = realizedPnl.negate(); // Short cover P&L is inverted
            }
        }

        // Update position
        if (quantityDelta > 0) {
            position.addQuantity(quantityDelta, price);
        } else {
            position.removeQuantity(-quantityDelta, price);
        }

        position.updateMarketPrice(price);
        position.setUpdatedAt(LocalDateTime.now());
        positionRepository.save(position);

        log.debug("[TRADE] Updated position for account {}: {} {} @ {} (realized P&L: {})",
                accountId, symbol, position.getQuantity(), calculateAverageCost(position), realizedPnl);
    }

    private void updateAccountBalances(Trade trade) {
        BigDecimal notional = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));

        // Update buyer account (debit cash + commission)
        Account buyerAccount = accountRepository.findById(trade.getBuyerAccountId()).orElse(null);
        if (buyerAccount != null) {
            BigDecimal totalDebit = notional.add(trade.getBuyerCommission());
            buyerAccount.setCashBalance(buyerAccount.getCashBalance().subtract(totalDebit));
            accountRepository.save(buyerAccount);
        }

        // Update seller account (credit cash - commission)
        Account sellerAccount = accountRepository.findById(trade.getSellerAccountId()).orElse(null);
        if (sellerAccount != null) {
            BigDecimal totalCredit = notional.subtract(trade.getSellerCommission());
            sellerAccount.setCashBalance(sellerAccount.getCashBalance().add(totalCredit));
            accountRepository.save(sellerAccount);
        }
    }

    private void updateInstrumentStats(Trade trade) {
        instrumentRepository.updateTradeStats(
                trade.getSymbol(),
                trade.getPrice(),
                trade.getQuantity());

        // Set open price if this is first trade of day
        instrumentRepository.setOpenPrice(trade.getSymbol(), trade.getPrice());
    }

    private void publishTradeEvent(Trade trade) {
        try {
            kafkaTemplate.send("trades", trade.getSymbol(), TradeResponse.fromTrade(trade));
        } catch (Exception e) {
            log.warn("[TRADE] Failed to publish trade to Kafka: {}", e.getMessage());
        }
    }

    private BigDecimal calculateAverageCost(Position position) {
        long qty = position.getQuantity();
        if (qty == 0L) {
            return BigDecimal.ZERO;
        }
        BigDecimal absQty = BigDecimal.valueOf(Math.abs(qty));
        return position.getCostBasis()
                .divide(absQty, 10, RoundingMode.HALF_UP);
    }

    // ==================== Query Methods ====================

    /**
     * Get recent trades for a symbol.
     */
    public List<TradeResponse> getRecentTrades(String symbol, int limit) {
        return tradeRepository.findRecentBySymbol(symbol, PageRequest.of(0, limit))
                .stream()
                .map(TradeResponse::fromTrade)
                .toList();
    }

    /**
     * Get trades for an account.
     */
    public Page<TradeResponse> getTradesForAccount(Long accountId, Pageable pageable) {
        return tradeRepository.findByAccountId(accountId, pageable)
                .map(TradeResponse::fromTrade);
    }

    /**
     * Get trades for an order.
     */
    public List<TradeResponse> getTradesForOrder(Long orderId) {
        return tradeRepository.findByBuyOrderIdOrSellOrderId(orderId, orderId)
                .stream()
                .map(TradeResponse::fromTrade)
                .toList();
    }

    /**
     * Get trade volume for symbol.
     */
    public Long getVolume(String symbol, LocalDateTime since) {
        return tradeRepository.getTotalVolume(symbol, since);
    }

    /**
     * Get VWAP for symbol.
     */
    public BigDecimal getVWAP(String symbol, LocalDateTime since) {
        return tradeRepository.calculateVWAP(symbol, since);
    }

    // ==================== Settlement ====================

    /**
     * Process settlements for T+2 trades.
     */
    @Transactional
    public int processSettlements() {
        List<Trade> pendingTrades = tradeRepository.findPendingSettlement(LocalDate.now());
        int settled = 0;

        for (Trade trade : pendingTrades) {
            try {
                // In production, this would interact with clearing house
                trade.setSettlementStatus(Trade.SettlementStatus.SETTLED);
                tradeRepository.save(trade);
                settled++;

                log.debug("[SETTLEMENT] Settled trade {}", trade.getTradeId());
            } catch (Exception e) {
                log.error("[SETTLEMENT] Failed to settle trade {}: {}",
                        trade.getTradeId(), e.getMessage());
                trade.setSettlementStatus(Trade.SettlementStatus.FAILED);
                tradeRepository.save(trade);
            }
        }

        log.info("[SETTLEMENT] Processed {} settlements", settled);
        return settled;
    }

    // ==================== Statistics ====================

    /**
     * Get daily trading statistics.
     */
    public DailyStats getDailyStats(String symbol) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        Long volume = tradeRepository.getTotalVolume(symbol, startOfDay);
        BigDecimal value = tradeRepository.getTotalValue(symbol, startOfDay);
        long tradeCount = tradeRepository.countTrades(symbol, startOfDay);
        BigDecimal vwap = tradeRepository.calculateVWAP(symbol, startOfDay);

        Trade firstTrade = tradeRepository.findFirstTradeOfDay(symbol, startOfDay);
        Trade lastTrade = tradeRepository.findLastTrade(symbol, startOfDay);

        return DailyStats.builder()
                .symbol(symbol)
                .volume(volume != null ? volume : 0L)
                .value(value != null ? value : BigDecimal.ZERO)
                .tradeCount(tradeCount)
                .vwap(vwap)
                .openPrice(firstTrade != null ? firstTrade.getPrice() : null)
                .lastPrice(lastTrade != null ? lastTrade.getPrice() : null)
                .build();
    }

    @lombok.Value
    @lombok.Builder
    public static class DailyStats {
        String symbol;
        long volume;
        BigDecimal value;
        long tradeCount;
        BigDecimal vwap;
        BigDecimal openPrice;
        BigDecimal lastPrice;
    }
}
