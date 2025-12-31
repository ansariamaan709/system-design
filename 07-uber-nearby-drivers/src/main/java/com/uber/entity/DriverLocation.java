package com.uber.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Driver location entity storing the current location of a driver.
 * This is the hot-path data, primarily stored in Redis for fast access.
 * PostgreSQL serves as persistent backup.
 */
@Entity
@Table(name = "driver_locations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocation {
    
    @Id
    @Column(name = "driver_id")
    private UUID driverId;
    
    @Column(nullable = false)
    private Double latitude;
    
    @Column(nullable = false)
    private Double longitude;
    
    /**
     * Geohash encoding of the location.
     * Used for Redis GEO operations and spatial indexing.
     * Precision 7 gives ~76m accuracy.
     */
    @Column(nullable = false, length = 12)
    private String geohash;
    
    /**
     * H3 hexagonal grid index.
     * Uber's preferred spatial indexing system.
     * Resolution 9 gives ~174m edge cells.
     */
    @Column(name = "h3_index", nullable = false)
    private Long h3Index;
    
    /**
     * Direction the driver is heading (0-359 degrees).
     * 0 = North, 90 = East, 180 = South, 270 = West
     */
    private Short heading;
    
    /**
     * Current speed in meters per second.
     */
    private Float speed;
    
    /**
     * GPS accuracy in meters.
     * Lower is more accurate.
     */
    private Float accuracy;
    
    /**
     * Current driver status.
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DriverStatus status = DriverStatus.AVAILABLE;
    
    /**
     * Timestamp of the last location update.
     */
    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * Check if this location is stale (older than threshold).
     */
    public boolean isStale(long thresholdMillis) {
        return Instant.now().toEpochMilli() - updatedAt.toEpochMilli() > thresholdMillis;
    }
    
    /**
     * Check if driver is available for rides.
     */
    public boolean isAvailable() {
        return status == DriverStatus.AVAILABLE;
    }
}
