package com.stockexchange.repository;

import com.stockexchange.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

        Optional<Instrument> findBySymbol(String symbol);

        boolean existsBySymbol(String symbol);

        List<Instrument> findByTradingStatus(Instrument.TradingStatus status);

        @Query("SELECT i FROM Instrument i WHERE i.tradable = true " +
                        "AND i.tradingStatus IN ('PRE_MARKET', 'OPEN', 'AFTER_HOURS')")
        List<Instrument> findTradableInstruments();

        @Query("SELECT i FROM Instrument i WHERE i.instrumentType = :type AND i.tradable = true")
        List<Instrument> findByType(@Param("type") Instrument.InstrumentType type);

        // Search by name or symbol
        @Query("SELECT i FROM Instrument i WHERE " +
                        "LOWER(i.symbol) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%'))")
        List<Instrument> search(@Param("query") String query);

        // Update trading status
        @Modifying
        @Query("UPDATE Instrument i SET i.tradingStatus = :status WHERE i.symbol = :symbol")
        int updateTradingStatus(@Param("symbol") String symbol, @Param("status") Instrument.TradingStatus status);

        // Update last trade price
        @Modifying
        @Query("UPDATE Instrument i SET " +
                        "i.lastPrice = :price, " +
                        "i.high = CASE WHEN :price > i.high OR i.high IS NULL THEN :price ELSE i.high END, " +
                        "i.highPrice = CASE WHEN :price > i.highPrice OR i.highPrice IS NULL THEN :price ELSE i.highPrice END, "
                        +
                        "i.low = CASE WHEN :price < i.low OR i.low IS NULL THEN :price ELSE i.low END, " +
                        "i.lowPrice = CASE WHEN :price < i.lowPrice OR i.lowPrice IS NULL THEN :price ELSE i.lowPrice END, "
                        +
                        "i.volume = i.volume + :quantity, " +
                        "i.volumeToday = i.volumeToday + :quantity, " +
                        "i.valueTraded = i.valueTraded + (:price * :quantity), " +
                        "i.valueTradedToday = i.valueTradedToday + (:price * :quantity), " +
                        "i.lastTradeTime = CURRENT_TIMESTAMP " +
                        "WHERE i.symbol = :symbol")
        int updateTradeStats(@Param("symbol") String symbol,
                        @Param("price") BigDecimal price,
                        @Param("quantity") Long quantity);

        // Set open price (at market open)
        @Modifying
        @Query("UPDATE Instrument i SET i.open = :price, i.openPrice = :price WHERE i.symbol = :symbol AND i.open IS NULL")
        int setOpenPrice(@Param("symbol") String symbol, @Param("price") BigDecimal price);

        // Reset daily stats (at market open)
        @Modifying
        @Query("UPDATE Instrument i SET " +
                        "i.previousClose = i.lastPrice, " +
                        "i.prevClose = i.lastPrice, " +
                        "i.open = NULL, " +
                        "i.openPrice = NULL, " +
                        "i.high = NULL, " +
                        "i.highPrice = NULL, " +
                        "i.low = NULL, " +
                        "i.lowPrice = NULL, " +
                        "i.volume = 0, " +
                        "i.volumeToday = 0, " +
                        "i.valueTraded = 0, " +
                        "i.valueTradedToday = 0 " +
                        "WHERE i.tradable = true")
        int resetDailyStats();

        // Halt trading
        @Modifying
        @Query("UPDATE Instrument i SET i.tradingStatus = 'HALTED', i.haltReason = :reason " +
                        "WHERE i.symbol = :symbol")
        int haltTrading(@Param("symbol") String symbol, @Param("reason") String reason);

        // Resume trading
        @Modifying
        @Query("UPDATE Instrument i SET i.tradingStatus = 'OPEN', i.haltReason = NULL " +
                        "WHERE i.symbol = :symbol")
        int resumeTrading(@Param("symbol") String symbol);

        // Find halted instruments
        @Query("SELECT i FROM Instrument i WHERE i.tradingStatus = 'HALTED'")
        List<Instrument> findHaltedInstruments();

        // Top gainers/losers
        @Query("SELECT i FROM Instrument i WHERE i.previousClose IS NOT NULL AND i.previousClose > 0 " +
                        "ORDER BY ((i.lastPrice - i.previousClose) / i.previousClose) DESC")
        List<Instrument> findTopGainers();

        @Query("SELECT i FROM Instrument i WHERE i.previousClose IS NOT NULL AND i.previousClose > 0 " +
                        "ORDER BY ((i.lastPrice - i.previousClose) / i.previousClose) ASC")
        List<Instrument> findTopLosers();

        // Most active by volume
        @Query("SELECT i FROM Instrument i ORDER BY i.volume DESC")
        List<Instrument> findMostActive();
}
