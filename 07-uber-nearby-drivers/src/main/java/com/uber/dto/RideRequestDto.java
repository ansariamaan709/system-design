package com.uber.dto;

import com.uber.entity.VehicleType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

/**
 * Request DTO for creating a ride request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideRequestDto {

    @NotNull(message = "Rider ID is required")
    private UUID riderId;

    @NotNull(message = "Pickup location is required")
    private Location pickupLocation;

    private Location dropoffLocation;

    private VehicleType vehicleType;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        private Double lat;

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        private Double lng;

        private String address;
    }
}
