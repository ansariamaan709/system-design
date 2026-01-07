package com.uber.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request DTO for driver location updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    private Double longitude;

    @Min(value = 0, message = "Heading must be >= 0")
    @Max(value = 359, message = "Heading must be <= 359")
    private Integer heading;

    @DecimalMin(value = "0.0", message = "Speed must be >= 0")
    @DecimalMax(value = "100.0", message = "Speed must be <= 100 m/s")
    private Float speed;

    @DecimalMin(value = "0.0", message = "Accuracy must be >= 0")
    @DecimalMax(value = "1000.0", message = "Accuracy must be <= 1000 meters")
    private Float accuracy;

    /**
     * Client-side timestamp in epoch milliseconds.
     * Used to detect stale updates.
     */
    private Long timestamp;
}
