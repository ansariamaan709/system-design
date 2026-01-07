package com.uber.dto;

import com.uber.entity.RideStatus;
import lombok.*;

import java.util.UUID;

/**
 * Response DTO for ride request creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideRequestResponse {

    private UUID requestId;
    private RideStatus status;
    private Integer estimatedWait; // Seconds
    private String message;

    public static RideRequestResponse created(UUID requestId, int estimatedWait) {
        return RideRequestResponse.builder()
                .requestId(requestId)
                .status(RideStatus.MATCHING)
                .estimatedWait(estimatedWait)
                .message("Searching for nearby drivers")
                .build();
    }

    public static RideRequestResponse noDrivers(UUID requestId) {
        return RideRequestResponse.builder()
                .requestId(requestId)
                .status(RideStatus.NO_DRIVERS)
                .estimatedWait(null)
                .message("No drivers available in your area. Please try again.")
                .build();
    }
}
