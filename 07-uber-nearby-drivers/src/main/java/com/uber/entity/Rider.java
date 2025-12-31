package com.uber.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Rider entity representing a registered rider in the system.
 */
@Entity
@Table(name = "riders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rider {
    
    @Id
    @Column(name = "rider_id")
    private UUID riderId;
    
    @Column(nullable = false)
    private String name;
    
    private String phone;
    
    private String email;
    
    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.valueOf(5.00);
    
    @Column(name = "total_trips")
    @Builder.Default
    private Integer totalTrips = 0;
    
    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    @PrePersist
    protected void onCreate() {
        if (riderId == null) {
            riderId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
