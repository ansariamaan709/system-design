package com.uber.controller;

import com.uber.dto.NearbyDriversResponse;
import com.uber.entity.VehicleType;
import com.uber.service.NearbySearchService;
import com.uber.spatial.HaversineDistance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for finding nearby drivers.
 * 
 * Endpoints:
 * - GET /api/v1/drivers/nearby - Find drivers within radius
 * - GET /api/v1/drivers/{driverId}/eta - Calculate ETA to a location
 */
@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@Slf4j
public class NearbyController {
    
    private final NearbySearchService nearbySearchService;
    
    /**
     * Find nearby available drivers within a radius.
     * Called by rider app to display drivers on map.
     *
     * @param lat         Rider's latitude
     * @param lng         Rider's longitude
     * @param radius      Search radius in meters (default: 5000)
     * @param limit       Maximum drivers to return (default: 20)
     * @param vehicleType Optional vehicle type filter
     * @param cityId      City identifier from header
     */
    @GetMapping("/nearby")
    public ResponseEntity<NearbyDriversResponse> findNearbyDrivers(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestHeader(value = "X-City-Id", defaultValue = "san_francisco") String cityId) {
        
        log.debug("Nearby search at ({}, {}) with radius {} in city {}", 
                lat, lng, radius, cityId);
        
        NearbyDriversResponse response = nearbySearchService.findNearbyDrivers(
                cityId, lat, lng, radius, limit, vehicleType);
        
        log.debug("Found {} nearby drivers in {}ms", 
                response.getTotalFound(), response.getSearchTimeMs());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Calculate ETA from a driver to a location.
     */
    @GetMapping("/{driverId}/eta")
    public ResponseEntity<EtaResponse> getDriverEta(
            @PathVariable String driverId,
            @RequestParam double toLat,
            @RequestParam double toLng,
            @RequestParam double fromLat,
            @RequestParam double fromLng) {
        
        double distance = HaversineDistance.calculateMeters(fromLat, fromLng, toLat, toLng);
        int etaSeconds = HaversineDistance.estimateEtaSeconds(fromLat, fromLng, toLat, toLng);
        
        return ResponseEntity.ok(new EtaResponse(
                driverId,
                etaSeconds,
                (int) distance
        ));
    }
    
    /**
     * Simple ETA response record.
     */
    public record EtaResponse(String driverId, int etaSeconds, int distanceMeters) {}
}
