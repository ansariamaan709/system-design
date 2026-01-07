package com.uber.dto;

import com.uber.entity.VehicleType;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO representing a nearby driver in search results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearbyDriver {

    private UUID driverId;
    private Double latitude;
    private Double longitude;
    private Double distance; // Distance in meters
    private Integer eta; // ETA in seconds
    private Integer heading; // Direction (0-359)
    private VehicleType vehicleType;
    private BigDecimal rating;

    /**
     * Create a NearbyDriver with calculated ETA.
     */
    public static NearbyDriver from(UUID driverId, double lat, double lng,
            double distance, VehicleType vehicleType,
            BigDecimal rating, int heading) {
        // Estimate ETA based on average urban speed (25 km/h = ~7 m/s)
        int eta = (int) Math.ceil(distance / 7.0);

        return NearbyDriver.builder()
                .driverId(driverId)
                .latitude(lat)
                .longitude(lng)
                .distance(distance)
                .eta(eta)
                .heading(heading)
                .vehicleType(vehicleType)
                .rating(rating)
                .build();
    }
}
