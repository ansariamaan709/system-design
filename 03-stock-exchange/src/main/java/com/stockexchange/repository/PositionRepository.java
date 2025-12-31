package com.stockexchange.repository;

import com.stockexchange.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

        Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);

        List<Position> findByAccountId(Long accountId);

        @Query("SELECT p FROM Position p WHERE p.accountId = :accountId AND p.quantity != 0")
        List<Position> findOpenPositionsByAccountId(@Param("accountId") Long accountId);

        @Query("SELECT p FROM Position p WHERE p.symbol = :symbol AND p.quantity != 0")
        List<Position> findOpenPositionsBySymbol(@Param("symbol") String symbol);

        // Get total position across all accounts for a symbol
        @Query("SELECT SUM(p.quantity) FROM Position p WHERE p.symbol = :symbol")
        Long getTotalPositionForSymbol(@Param("symbol") String symbol);

        // Update market price and P&L for a symbol
        @Modifying
        @Query("UPDATE Position p SET " +
                        "p.marketPrice = :marketPrice, " +
                        "p.currentPrice = :marketPrice, " +
                        "p.marketValue = p.quantity * :marketPrice, " +
                        "p.unrealizedPnl = (p.quantity * :marketPrice) - p.costBasis, " +
                        "p.updatedAt = CURRENT_TIMESTAMP " +
                        "WHERE p.symbol = :symbol AND p.quantity != 0")
        int updateMarketPrice(@Param("symbol") String symbol, @Param("marketPrice") BigDecimal marketPrice);

        // Update position after trade
        @Modifying
        @Query("UPDATE Position p SET " +
                        "p.quantity = p.quantity + :quantityDelta, " +
                        "p.costBasis = p.costBasis + :costDelta, " +
                        "p.marketValue = (p.quantity + :quantityDelta) * p.marketPrice, " +
                        "p.updatedAt = CURRENT_TIMESTAMP " +
                        "WHERE p.accountId = :accountId AND p.symbol = :symbol")
        int updatePosition(@Param("accountId") Long accountId,
                        @Param("symbol") String symbol,
                        @Param("quantityDelta") Long quantityDelta,
                        @Param("costDelta") BigDecimal costDelta);

        // Reset today's P&L
        @Modifying
        @Query("UPDATE Position p SET p.todayPnl = 0, p.realizedPnlToday = 0, p.previousClose = p.marketPrice")
        int resetDailyPnl();

        // Calculate total unrealized P&L for an account
        @Query("SELECT SUM(p.unrealizedPnl) FROM Position p WHERE p.accountId = :accountId")
        BigDecimal getTotalUnrealizedPnl(@Param("accountId") Long accountId);

        // Calculate portfolio value
        @Query("SELECT SUM(p.marketValue) FROM Position p WHERE p.accountId = :accountId")
        BigDecimal getPortfolioValue(@Param("accountId") Long accountId);

        // Find large positions (for risk monitoring)
        @Query("SELECT p FROM Position p WHERE ABS(p.quantity) > :threshold")
        List<Position> findLargePositions(@Param("threshold") Long threshold);

        // Find losing positions
        @Query("SELECT p FROM Position p WHERE p.unrealizedPnl < :threshold " +
                        "ORDER BY p.unrealizedPnl ASC")
        List<Position> findLosingPositions(@Param("threshold") BigDecimal threshold);

        // Count positions by symbol
        @Query("SELECT p.symbol, COUNT(p), SUM(p.quantity) FROM Position p " +
                        "WHERE p.quantity != 0 GROUP BY p.symbol")
        List<Object[]> getPositionSummaryBySymbol();
}
