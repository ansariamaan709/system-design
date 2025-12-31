package com.uber.repository;

import com.uber.entity.RideRequest;
import com.uber.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RideRequest entity operations.
 */
@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, UUID> {
    
    List<RideRequest> findByRiderId(UUID riderId);
    
    List<RideRequest> findByMatchedDriverId(UUID driverId);
    
    List<RideRequest> findByStatus(RideStatus status);
    
    List<RideRequest> findByRiderIdAndStatus(UUID riderId, RideStatus status);
    
    Optional<RideRequest> findByMatchedDriverIdAndStatus(UUID driverId, RideStatus status);
    
    @Query("SELECT r FROM RideRequest r WHERE r.riderId = :riderId AND r.status IN :statuses ORDER BY r.createdAt DESC")
    List<RideRequest> findActiveRidesForRider(@Param("riderId") UUID riderId, @Param("statuses") List<RideStatus> statuses);
    
    @Query("SELECT r FROM RideRequest r WHERE r.matchedDriverId = :driverId AND r.status IN :statuses")
    Optional<RideRequest> findActiveRideForDriver(@Param("driverId") UUID driverId, @Param("statuses") List<RideStatus> statuses);
    
    @Modifying
    @Query("UPDATE RideRequest r SET r.status = :status WHERE r.requestId = :requestId")
    int updateStatus(@Param("requestId") UUID requestId, @Param("status") RideStatus status);
    
    @Modifying
    @Query("UPDATE RideRequest r SET r.matchedDriverId = :driverId, r.matchedAt = :matchedAt, r.status = 'DRIVER_ASSIGNED' WHERE r.requestId = :requestId")
    int assignDriver(@Param("requestId") UUID requestId, @Param("driverId") UUID driverId, @Param("matchedAt") Instant matchedAt);
    
    @Query("SELECT COUNT(r) FROM RideRequest r WHERE r.status = :status AND r.createdAt > :since")
    long countByStatusSince(@Param("status") RideStatus status, @Param("since") Instant since);
    
    // Find pending requests in a given H3 cell (for demand calculation)
    @Query("SELECT r FROM RideRequest r WHERE r.status = 'PENDING' AND r.pickupH3 = :h3Index")
    List<RideRequest> findPendingInH3Cell(@Param("h3Index") Long h3Index);
}
