package com.stockexchange.repository;

import com.stockexchange.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

        // Trades by order
        List<Trade> findByBuyOrderIdOrSellOrderId(Long buyOrderId, Long sellOrderId);

        // Trades by account (buyer or seller)
        @Query("SELECT t FROM Trade t WHERE t.buyerAccountId = :accountId OR t.sellerAccountId = :accountId " +
                        "ORDER BY t.executedAt DESC")
        Page<Trade> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);

        // Recent trades for a symbol
        @Query("SELECT t FROM Trade t WHERE t.symbol = :symbol ORDER BY t.executedAt DESC")
        List<Trade> findRecentBySymbol(@Param("symbol") String symbol, Pageable pageable);

        // Trades for settlement
        @Query("SELECT t FROM Trade t WHERE t.settlementDate = :date " +
                        "AND t.settlementStatus = 'PENDING'")
        List<Trade> findPendingSettlement(@Param("date") LocalDate date);

        // Trade history for a symbol in time range
        @Query("SELECT t FROM Trade t WHERE t.symbol = :symbol " +
                        "AND t.executedAt BETWEEN :startTime AND :endTime " +
                        "ORDER BY t.executedAt DESC")
        List<Trade> findBySymbolAndTimeRange(
                        @Param("symbol") String symbol,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

        // Volume statistics
        @Query("SELECT SUM(t.quantity) FROM Trade t WHERE t.symbol = :symbol " +
                        "AND t.executedAt > :since")
        Long getTotalVolume(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

        @Query("SELECT SUM(t.tradeValue) FROM Trade t WHERE t.symbol = :symbol " +
                        "AND t.executedAt > :since")
        BigDecimal getTotalValue(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

        // OHLCV data
        @Query(value = "SELECT " +
                        "MIN(t.price) as low, " +
                        "MAX(t.price) as high, " +
                        "SUM(t.quantity) as volume, " +
                        "SUM(t.trade_value) as value_traded " +
                        "FROM trades t " +
                        "WHERE t.symbol = :symbol " +
                        "AND t.executed_at > :since", nativeQuery = true)
        Object[] getDailyStats(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

        // First and last trade of day
        @Query("SELECT t FROM Trade t WHERE t.symbol = :symbol " +
                        "AND t.executedAt > :since ORDER BY t.executedAt ASC LIMIT 1")
        Trade findFirstTradeOfDay(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

        @Query("SELECT t FROM Trade t WHERE t.symbol = :symbol " +
                        "AND t.executedAt > :since ORDER BY t.executedAt DESC LIMIT 1")
        Trade findLastTrade(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

        // VWAP calculation
        @Query("SELECT SUM(t.price * t.quantity) / SUM(t.quantity) FROM Trade t " +
                        "WHERE t.symbol = :symbol AND t.executedAt > :since")
        BigDecimal calculateVWAP(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

        // Trade count
        @Query("SELECT COUNT(t) FROM Trade t WHERE t.symbol = :symbol " +
                        "AND t.executedAt > :since")
        long countTrades(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

        // Top traded symbols
        @Query("SELECT t.symbol, SUM(t.tradeValue) as total_value FROM Trade t " +
                        "WHERE t.executedAt > :since " +
                        "GROUP BY t.symbol " +
                        "ORDER BY total_value DESC")
        List<Object[]> getTopTradedSymbols(@Param("since") LocalDateTime since, Pageable pageable);

        // Account P&L from trades
        @Query("SELECT SUM(CASE " +
                        "WHEN t.buyerAccountId = :accountId THEN -t.tradeValue " +
                        "WHEN t.sellerAccountId = :accountId THEN t.tradeValue " +
                        "ELSE 0 END) " +
                        "FROM Trade t " +
                        "WHERE (t.buyerAccountId = :accountId OR t.sellerAccountId = :accountId) " +
                        "AND t.executedAt > :since")
        BigDecimal calculatePnL(@Param("accountId") Long accountId, @Param("since") LocalDateTime since);
}
