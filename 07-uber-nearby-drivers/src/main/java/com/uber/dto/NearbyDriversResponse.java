package com.uber.dto;

import lombok.*;

import java.util.List;

/**
 * Response DTO for nearby drivers search.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearbyDriversResponse {

    private List<NearbyDriver> drivers;
    private int searchRadiusUsed;
    private int totalFound;
    private long searchTimeMs;

    public static NearbyDriversResponse empty(int radiusUsed, long timeMs) {
        return NearbyDriversResponse.builder()
                .drivers(List.of())
                .searchRadiusUsed(radiusUsed)
                .totalFound(0)
                .searchTimeMs(timeMs)
                .build();
    }

    public static NearbyDriversResponse of(List<NearbyDriver> drivers, int radiusUsed, long timeMs) {
        return NearbyDriversResponse.builder()
                .drivers(drivers)
                .searchRadiusUsed(radiusUsed)
                .totalFound(drivers.size())
                .searchTimeMs(timeMs)
                .build();
    }
}
