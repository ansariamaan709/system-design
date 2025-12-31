package com.uber.controller;

import com.uber.dto.BatchLocationUpdateRequest;
import com.uber.dto.BatchLocationUpdateResponse;
import com.uber.dto.DriverStatusRequest;
import com.uber.dto.LocationUpdateRequest;
import com.uber.entity.DriverLocation;
import com.uber.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for driver location operations.
 * 
 * Endpoints:
 * - POST /api/v1/drivers/{driverId}/location - Update single driver location
 * - POST /api/v1/locations/batch - Batch update multiple locations
 * - PUT /api/v1/drivers/{driverId}/status - Update driver status
 * - GET /api/v1/drivers/{driverId}/location - Get current driver location
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class LocationController {
    
    private final LocationService locationService;
    
    /**
     * Update a driver's current location.
     * Called by driver app every 4 seconds when moving.
     */
    @PostMapping("/drivers/{driverId}/location")
    public ResponseEntity<Void> updateLocation(
            @PathVariable UUID driverId,
            @RequestHeader(value = "X-City-Id", defaultValue = "san_francisco") String cityId,
            @Valid @RequestBody LocationUpdateRequest request) {
        
        log.debug("Location update for driver {} in city {}: ({}, {})", 
                driverId, cityId, request.getLatitude(), request.getLongitude());
        
        locationService.updateLocation(driverId, cityId, request);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Batch update multiple driver locations.
     * High-throughput endpoint for bulk ingestion.
     */
    @PostMapping("/locations/batch")
    public ResponseEntity<BatchLocationUpdateResponse> batchUpdateLocations(
            @RequestHeader(value = "X-City-Id", defaultValue = "san_francisco") String cityId,
            @Valid @RequestBody BatchLocationUpdateRequest request) {
        
        log.debug("Batch location update: {} locations for city {}", 
                request.getLocations().size(), cityId);
        
        BatchLocationUpdateResponse response = locationService.batchUpdateLocations(cityId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update driver availability status.
     */
    @PutMapping("/drivers/{driverId}/status")
    public ResponseEntity<Void> updateDriverStatus(
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverStatusRequest request) {
        
        log.info("Status update for driver {}: {}", driverId, request.getStatus());
        
        locationService.updateDriverStatus(driverId, request.getStatus());
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get current location of a driver.
     */
    @GetMapping("/drivers/{driverId}/location")
    public ResponseEntity<DriverLocation> getDriverLocation(@PathVariable UUID driverId) {
        DriverLocation location = locationService.getDriverLocation(driverId);
        
        if (location == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(location);
    }
}
