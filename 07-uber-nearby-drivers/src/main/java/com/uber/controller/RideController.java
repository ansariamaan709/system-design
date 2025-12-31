package com.uber.controller;

import com.uber.dto.RideRequestDto;
import com.uber.dto.RideRequestResponse;
import com.uber.entity.RideRequest;
import com.uber.entity.RideStatus;
import com.uber.repository.RideRequestRepository;
import com.uber.service.MatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for ride request operations.
 * 
 * Endpoints:
 * - POST /api/v1/rides/request - Create a new ride request
 * - GET /api/v1/rides/{requestId} - Get ride request status
 * - POST /api/v1/rides/{requestId}/cancel - Cancel a ride request
 */
@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
@Slf4j
public class RideController {
    
    private final MatchingService matchingService;
    private final RideRequestRepository rideRequestRepository;
    
    /**
     * Create a new ride request.
     * This initiates the driver matching process.
     */
    @PostMapping("/request")
    public ResponseEntity<RideRequestResponse> requestRide(
            @Valid @RequestBody RideRequestDto request,
            @RequestHeader(value = "X-City-Id", defaultValue = "san_francisco") String cityId) {
        
        log.info("New ride request from rider {} in city {}", request.getRiderId(), cityId);
        
        RideRequestResponse response = matchingService.requestRide(request, cityId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get the current status of a ride request.
     */
    @GetMapping("/{requestId}")
    public ResponseEntity<RideRequestStatus> getRideStatus(@PathVariable UUID requestId) {
        return rideRequestRepository.findById(requestId)
                .map(request -> ResponseEntity.ok(new RideRequestStatus(
                        request.getRequestId(),
                        request.getStatus(),
                        request.getMatchedDriverId(),
                        request.getMatchedAt() != null ? request.getMatchedAt().toEpochMilli() : null
                )))
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Cancel a ride request.
     */
    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<Void> cancelRide(
            @PathVariable UUID requestId,
            @RequestBody(required = false) CancelRequest cancelRequest) {
        
        return rideRequestRepository.findById(requestId)
                .map(request -> {
                    if (!request.isActive()) {
                        return ResponseEntity.badRequest().<Void>build();
                    }
                    
                    String reason = cancelRequest != null ? cancelRequest.reason() : "User cancelled";
                    request.cancel(reason);
                    rideRequestRepository.save(request);
                    
                    log.info("Ride {} cancelled: {}", requestId, reason);
                    
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Response record for ride status.
     */
    public record RideRequestStatus(
            UUID requestId,
            RideStatus status,
            UUID matchedDriverId,
            Long matchedAt) {}
    
    /**
     * Request record for cancellation.
     */
    public record CancelRequest(String reason) {}
}
