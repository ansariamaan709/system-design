package com.uber.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for batch location updates (high-throughput path).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchLocationUpdateRequest {

    @NotEmpty(message = "Locations list cannot be empty")
    @Size(max = 1000, message = "Maximum 1000 locations per batch")
    @Valid
    private List<DriverLocationUpdate> locations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverLocationUpdate {

        @NotNull(message = "Driver ID is required")
        private UUID driverId;

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        private Double lat;

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        private Double lng;

        private Integer heading;

        private Float speed;

        private Float accuracy;

        @NotNull(message = "Timestamp is required")
        private Long ts;
    }
}
