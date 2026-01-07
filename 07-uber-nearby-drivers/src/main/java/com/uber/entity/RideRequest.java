package com.uber.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ride request entity representing a ride request from a rider.
 */
@Entity
@Table(name = "ride_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideRequest {

    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "rider_id", nullable = false)
    private UUID riderId;

    // Pickup location
    @Column(name = "pickup_latitude", nullable = false)
    private Double pickupLatitude;

    @Column(name = "pickup_longitude", nullable = false)
    private Double pickupLongitude;

    @Column(name = "pickup_h3", nullable = false)
    private Long pickupH3;

    @Column(name = "pickup_address")
    private String pickupAddress;

    // Dropoff location
    @Column(name = "dropoff_latitude")
    private Double dropoffLatitude;

    @Column(name = "dropoff_longitude")
    private Double dropoffLongitude;

    @Column(name = "dropoff_address")
    private String dropoffAddress;

    @Column(name = "vehicle_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VehicleType vehicleType = VehicleType.UBERX;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RideStatus status = RideStatus.PENDING;

    @Column(name = "matched_driver_id")
    private UUID matchedDriverId;

    @Column(name = "search_radius_m")
    @Builder.Default
    private Integer searchRadiusM = 5000;

    @Column(name = "surge_multiplier", precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal surgeMultiplier = BigDecimal.ONE;

    @Column(name = "estimated_fare", precision = 10, scale = 2)
    private BigDecimal estimatedFare;

    @Column(name = "actual_fare", precision = 10, scale = 2)
    private BigDecimal actualFare;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(name = "pickup_at")
    private Instant pickupAt;

    @Column(name = "dropoff_at")
    private Instant dropoffAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @PrePersist
    protected void onCreate() {
        if (requestId == null) {
            requestId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    /**
     * Check if the request is still active.
     */
    public boolean isActive() {
        return status != RideStatus.COMPLETED &&
                status != RideStatus.CANCELLED &&
                status != RideStatus.NO_DRIVERS;
    }

    /**
     * Mark the ride as matched with a driver.
     */
    public void assignDriver(UUID driverId) {
        this.matchedDriverId = driverId;
        this.matchedAt = Instant.now();
        this.status = RideStatus.DRIVER_ASSIGNED;
    }

    /**
     * Cancel the ride.
     */
    public void cancel(String reason) {
        this.cancelledAt = Instant.now();
        this.cancelReason = reason;
        this.status = RideStatus.CANCELLED;
    }
}
