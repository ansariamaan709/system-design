package com.uber.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Driver entity representing a registered driver in the system.
 */
@Entity
@Table(name = "drivers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Driver {
    
    @Id
    @Column(name = "driver_id")
    private UUID driverId;
    
    @Column(nullable = false)
    private String name;
    
    private String phone;
    
    private String email;
    
    @Column(name = "vehicle_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;
    
    @Column(name = "vehicle_info", columnDefinition = "jsonb")
    private String vehicleInfo;
    
    @Column(name = "license_plate")
    private String licensePlate;
    
    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.valueOf(5.00);
    
    @Column(name = "total_trips")
    @Builder.Default
    private Integer totalTrips = 0;
    
    @Column(name = "acceptance_rate", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal acceptanceRate = BigDecimal.ONE;
    
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DriverStatus status = DriverStatus.OFFLINE;
    
    @Column(name = "city_id")
    private String cityId;
    
    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    @PrePersist
    protected void onCreate() {
        if (driverId == null) {
            driverId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
