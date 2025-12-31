package com.stockexchange.repository;

import com.stockexchange.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByClientId(Long clientId);

    Optional<Account> findByClientIdAndCurrency(Long clientId, String currency);

    @Query("SELECT a FROM Account a WHERE a.clientId = :clientId AND a.status = 'ACTIVE'")
    List<Account> findActiveByClientId(@Param("clientId") Long clientId);

    // Check if account can trade
    @Query("SELECT a.canTrade FROM Account a WHERE a.accountId = :accountId")
    Boolean canTrade(@Param("accountId") Long accountId);

    // Update buying power
    @Modifying
    @Query("UPDATE Account a SET a.buyingPower = a.buyingPower - :amount " +
            "WHERE a.accountId = :accountId AND a.buyingPower >= :amount")
    int reserveBuyingPower(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE Account a SET a.buyingPower = a.buyingPower + :amount " +
            "WHERE a.accountId = :accountId")
    int releaseBuyingPower(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);

    // Update cash balance
    @Modifying
    @Query("UPDATE Account a SET a.cashBalance = a.cashBalance + :amount, " +
            "a.buyingPower = a.buyingPower + :amount " +
            "WHERE a.accountId = :accountId")
    int creditCash(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE Account a SET a.cashBalance = a.cashBalance - :amount, " +
            "a.buyingPower = a.buyingPower - :amount " +
            "WHERE a.accountId = :accountId AND a.cashBalance >= :amount")
    int debitCash(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);

    // Update P&L
    @Modifying
    @Query("UPDATE Account a SET a.realizedPnl = a.realizedPnl + :pnl " +
            "WHERE a.accountId = :accountId")
    int addRealizedPnl(@Param("accountId") Long accountId, @Param("pnl") BigDecimal pnl);

    @Modifying
    @Query("UPDATE Account a SET a.unrealizedPnl = :pnl WHERE a.accountId = :accountId")
    int updateUnrealizedPnl(@Param("accountId") Long accountId, @Param("pnl") BigDecimal pnl);

    // Find accounts exceeding loss limits
    @Query("SELECT a FROM Account a WHERE a.realizedPnl < -a.dailyLossLimit")
    List<Account> findAccountsExceedingDailyLossLimit();

    // Find accounts with margin calls
    @Query("SELECT a FROM Account a WHERE a.marginEnabled = true " +
            "AND a.equity < a.marginUsed * 0.25") // 25% maintenance margin
    List<Account> findAccountsWithMarginCalls();

    // Reset daily P&L (for daily reconciliation)
    @Modifying
    @Query("UPDATE Account a SET a.realizedPnl = 0, a.unrealizedPnl = 0")
    int resetDailyPnl();

    // Statistics
    @Query("SELECT SUM(a.equity) FROM Account a WHERE a.status = 'ACTIVE'")
    BigDecimal getTotalEquity();

    @Query("SELECT COUNT(a) FROM Account a WHERE a.status = 'ACTIVE' AND a.canTrade = true")
    long countTradingAccounts();
}
