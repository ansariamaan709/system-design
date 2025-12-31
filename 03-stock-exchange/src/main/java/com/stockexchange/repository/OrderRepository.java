package com.stockexchange.repository;

import com.stockexchange.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

        Optional<Order> findByOrderIdAndClientId(Long orderId, Long clientId);

        Optional<Order> findByClientOrderIdAndClientId(String clientOrderId, Long clientId);

        List<Order> findByClientIdAndStatusIn(Long clientId, List<Order.OrderStatus> statuses);

        Page<Order> findByClientId(Long clientId, Pageable pageable);

        @Query("SELECT o FROM Order o WHERE o.clientId = :clientId " +
                        "AND (:symbol IS NULL OR o.symbol = :symbol) " +
                        "AND (:status IS NULL OR o.status = :status) " +
                        "AND (:side IS NULL OR o.side = :side) " +
                        "ORDER BY o.createdAt DESC")
        Page<Order> findByClientIdWithFilters(
                        @Param("clientId") Long clientId,
                        @Param("symbol") String symbol,
                        @Param("status") Order.OrderStatus status,
                        @Param("side") Order.Side side,
                        Pageable pageable);

        // Active orders (can be filled or cancelled)
        @Query("SELECT o FROM Order o WHERE o.clientId = :clientId " +
                        "AND o.status IN ('NEW', 'PARTIALLY_FILLED', 'PENDING_NEW', 'PENDING_CANCEL')")
        List<Order> findActiveOrdersByClientId(@Param("clientId") Long clientId);

        @Query("SELECT o FROM Order o WHERE o.symbol = :symbol " +
                        "AND o.status IN ('NEW', 'PARTIALLY_FILLED')")
        List<Order> findActiveOrdersBySymbol(@Param("symbol") String symbol);

        // Orders for a specific account
        List<Order> findByAccountIdAndStatusIn(Long accountId, List<Order.OrderStatus> statuses);

        // Count active orders (for rate limiting)
        @Query("SELECT COUNT(o) FROM Order o WHERE o.clientId = :clientId " +
                        "AND o.createdAt > :since " +
                        "AND o.status NOT IN ('FILLED', 'CANCELLED', 'REJECTED', 'EXPIRED')")
        long countActiveOrdersSince(@Param("clientId") Long clientId, @Param("since") Instant since);

        // Find expired orders
        @Query("SELECT o FROM Order o WHERE o.timeInForce = 'GTD' " +
                        "AND o.expireTime < :now " +
                        "AND o.status IN ('NEW', 'PARTIALLY_FILLED')")
        List<Order> findExpiredOrders(@Param("now") Instant now);

        // Find DAY orders to expire at market close
        @Query("SELECT o FROM Order o WHERE o.timeInForce = 'DAY' " +
                        "AND o.status IN ('NEW', 'PARTIALLY_FILLED')")
        List<Order> findDayOrdersToExpire();

        // Bulk update status
        @Modifying
        @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = :now " +
                        "WHERE o.orderId IN :orderIds")
        int bulkUpdateStatus(@Param("orderIds") List<Long> orderIds,
                        @Param("newStatus") Order.OrderStatus newStatus,
                        @Param("now") Instant now);

        // Statistics
        @Query("SELECT COUNT(o) FROM Order o WHERE o.symbol = :symbol " +
                        "AND o.createdAt > :since")
        long countOrdersSince(@Param("symbol") String symbol, @Param("since") Instant since);

        @Query("SELECT o.symbol, COUNT(o) FROM Order o " +
                        "WHERE o.createdAt > :since " +
                        "GROUP BY o.symbol " +
                        "ORDER BY COUNT(o) DESC")
        List<Object[]> getOrderCountBySymbol(@Param("since") Instant since);
}
