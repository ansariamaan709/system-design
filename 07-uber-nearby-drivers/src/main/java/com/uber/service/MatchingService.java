package com.uber.service;

import com.uber.dto.NearbyDriver;
import com.uber.dto.RideRequestDto;
import com.uber.dto.RideRequestResponse;
import com.uber.entity.*;
import com.uber.repository.RideRequestRepository;
import com.uber.spatial.H3Index;
import com.uber.spatial.HaversineDistance;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.uber.config.KafkaConfig.TOPIC_RIDE_MATCHES;
import static com.uber.config.KafkaConfig.TOPIC_RIDE_REQUESTS;

/**
 * Service for matching riders with drivers.
 * 
 * Matching Algorithm:
 * 1. Find nearby available drivers
 * 2. Score each driver based on multiple factors
 * 3. Select optimal driver
 * 4. Dispatch ride offer to driver
 * 5. Wait for driver response
 * 6. If rejected, try next best driver
 * 
 * Scoring factors:
 * - Distance/ETA (40%)
 * - Driver rating (20%)
 * - Acceptance rate (20%)
 * - ETA to pickup (15%)
 * - Surge penalty (5%)
 */
@Service
@Slf4j
public class MatchingService {

    private final NearbySearchService nearbySearchService;
    private final LocationService locationService;
    private final RideRequestRepository rideRequestRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private final Counter matchAttemptCounter;
    private final Counter matchSuccessCounter;
    private final Counter matchFailureCounter;

    @Value("${uber.matching.distance-weight:0.4}")
    private double distanceWeight;

    @Value("${uber.matching.rating-weight:0.2}")
    private double ratingWeight;

    @Value("${uber.matching.acceptance-rate-weight:0.2}")
    private double acceptanceRateWeight;

    @Value("${uber.matching.eta-weight:0.15}")
    private double etaWeight;

    @Value("${uber.matching.surge-penalty-weight:0.05}")
    private double surgePenaltyWeight;

    @Value("${uber.matching.match-timeout-seconds:30}")
    private int matchTimeoutSeconds;

    @Value("${uber.matching.driver-response-timeout-seconds:15}")
    private int driverResponseTimeoutSeconds;

    public MatchingService(
            NearbySearchService nearbySearchService,
            LocationService locationService,
            RideRequestRepository rideRequestRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.nearbySearchService = nearbySearchService;
        this.locationService = locationService;
        this.rideRequestRepository = rideRequestRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;

        this.matchAttemptCounter = Counter.builder("matching.attempts.total")
                .description("Total match attempts")
                .register(meterRegistry);

        this.matchSuccessCounter = Counter.builder("matching.success.total")
                .description("Successful matches")
                .register(meterRegistry);

        this.matchFailureCounter = Counter.builder("matching.failures.total")
                .description("Failed matches")
                .register(meterRegistry);
    }

    /**
     * Create a ride request and begin matching.
     */
    @Transactional
    public RideRequestResponse requestRide(RideRequestDto dto, String cityId) {
        matchAttemptCounter.increment();

        // Calculate H3 index for pickup location
        long pickupH3 = H3Index.latLngToCell(
                dto.getPickupLocation().getLat(),
                dto.getPickupLocation().getLng());

        // Create ride request entity
        RideRequest request = RideRequest.builder()
                .riderId(dto.getRiderId())
                .pickupLatitude(dto.getPickupLocation().getLat())
                .pickupLongitude(dto.getPickupLocation().getLng())
                .pickupH3(pickupH3)
                .pickupAddress(dto.getPickupLocation().getAddress())
                .vehicleType(dto.getVehicleType() != null ? dto.getVehicleType() : VehicleType.UBERX)
                .status(RideStatus.PENDING)
                .build();

        if (dto.getDropoffLocation() != null) {
            request.setDropoffLatitude(dto.getDropoffLocation().getLat());
            request.setDropoffLongitude(dto.getDropoffLocation().getLng());
            request.setDropoffAddress(dto.getDropoffLocation().getAddress());
        }

        // Save the request
        request = rideRequestRepository.save(request);

        // Publish to Kafka for async matching
        publishRideRequest(request);

        // Start async matching process
        final RideRequest finalRequest = request;
        CompletableFuture.runAsync(() -> matchDriver(finalRequest, cityId));

        // Estimate wait time based on nearby drivers
        int estimatedWait = estimateWaitTime(cityId,
                dto.getPickupLocation().getLat(),
                dto.getPickupLocation().getLng(),
                dto.getVehicleType());

        return RideRequestResponse.created(request.getRequestId(), estimatedWait);
    }

    /**
     * Main matching algorithm.
     */
    private void matchDriver(RideRequest request, String cityId) {
        log.info("Starting match for ride request {}", request.getRequestId());

        long startTime = System.currentTimeMillis();
        long deadline = startTime + (matchTimeoutSeconds * 1000L);

        // Find nearby drivers
        var nearbyResponse = nearbySearchService.findNearbyDriversWithExpansion(
                cityId,
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                5, // Minimum 5 drivers
                request.getVehicleType());

        if (nearbyResponse.getDrivers().isEmpty()) {
            log.warn("No drivers found for ride request {}", request.getRequestId());
            updateRequestStatus(request.getRequestId(), RideStatus.NO_DRIVERS);
            matchFailureCounter.increment();
            return;
        }

        // Score and rank drivers
        List<ScoredDriver> rankedDrivers = scoreDrivers(request, nearbyResponse.getDrivers());

        // Try to match with drivers in order
        for (ScoredDriver scoredDriver : rankedDrivers) {
            if (System.currentTimeMillis() > deadline) {
                log.warn("Match timeout for ride request {}", request.getRequestId());
                break;
            }

            boolean accepted = dispatchToDriver(request, scoredDriver);

            if (accepted) {
                // Match successful
                request.assignDriver(scoredDriver.driverId);
                rideRequestRepository.save(request);

                publishMatchEvent(request, scoredDriver);

                log.info("Matched ride {} with driver {} (score: {})",
                        request.getRequestId(), scoredDriver.driverId, scoredDriver.score);

                matchSuccessCounter.increment();
                return;
            }

            log.debug("Driver {} rejected/unavailable for ride {}",
                    scoredDriver.driverId, request.getRequestId());
        }

        // No driver accepted
        updateRequestStatus(request.getRequestId(), RideStatus.NO_DRIVERS);
        matchFailureCounter.increment();
    }

    /**
     * Score drivers based on multiple factors.
     */
    private List<ScoredDriver> scoreDrivers(RideRequest request, List<NearbyDriver> drivers) {
        List<ScoredDriver> scored = new ArrayList<>();

        // Normalize factors
        double maxDistance = drivers.stream()
                .mapToDouble(NearbyDriver::getDistance)
                .max()
                .orElse(1.0);

        double maxEta = drivers.stream()
                .mapToInt(NearbyDriver::getEta)
                .max()
                .orElse(1);

        for (NearbyDriver driver : drivers) {
            double distanceScore = 1.0 - (driver.getDistance() / maxDistance);
            double etaScore = 1.0 - (driver.getEta() / (double) maxEta);
            double ratingScore = driver.getRating().doubleValue() / 5.0;
            double acceptanceScore = 0.9; // Would come from driver profile

            // Calculate total score
            double totalScore = distanceWeight * distanceScore +
                    ratingWeight * ratingScore +
                    acceptanceRateWeight * acceptanceScore +
                    etaWeight * etaScore;

            scored.add(new ScoredDriver(
                    driver.getDriverId(),
                    driver.getLatitude(),
                    driver.getLongitude(),
                    driver.getDistance(),
                    driver.getEta(),
                    driver.getRating(),
                    totalScore));
        }

        // Sort by score descending
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        return scored;
    }

    /**
     * Dispatch ride offer to driver.
     * Returns true if driver accepts, false otherwise.
     */
    private boolean dispatchToDriver(RideRequest request, ScoredDriver driver) {
        // In a real system, this would:
        // 1. Send push notification to driver app
        // 2. Wait for response with timeout
        // 3. Handle acceptance/rejection

        // For demo, simulate acceptance based on score
        // Higher scored drivers are more likely to accept
        double acceptanceProbability = 0.7 + (driver.score * 0.2);

        try {
            // Simulate driver response time
            Thread.sleep(100 + (long) (Math.random() * 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return Math.random() < acceptanceProbability;
    }

    /**
     * Estimate wait time based on nearby driver availability.
     */
    private int estimateWaitTime(String cityId, double lat, double lng, VehicleType vehicleType) {
        var nearbyResponse = nearbySearchService.findNearbyDrivers(
                cityId, lat, lng, 5000, 5, vehicleType);

        if (nearbyResponse.getDrivers().isEmpty()) {
            return 300; // 5 minutes if no nearby drivers
        }

        // Average ETA of closest drivers + buffer
        double avgEta = nearbyResponse.getDrivers().stream()
                .mapToInt(NearbyDriver::getEta)
                .average()
                .orElse(180);

        return (int) (avgEta * 1.2); // 20% buffer
    }

    @Transactional
    private void updateRequestStatus(UUID requestId, RideStatus status) {
        rideRequestRepository.updateStatus(requestId, status);
    }

    private void publishRideRequest(RideRequest request) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "RIDE_REQUESTED");
        event.put("requestId", request.getRequestId().toString());
        event.put("riderId", request.getRiderId().toString());
        event.put("pickupLat", request.getPickupLatitude());
        event.put("pickupLng", request.getPickupLongitude());
        event.put("vehicleType", request.getVehicleType().name());
        event.put("timestamp", System.currentTimeMillis());

        kafkaTemplate.send(TOPIC_RIDE_REQUESTS, request.getRequestId().toString(), event);
    }

    private void publishMatchEvent(RideRequest request, ScoredDriver driver) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "RIDE_MATCHED");
        event.put("requestId", request.getRequestId().toString());
        event.put("driverId", driver.driverId.toString());
        event.put("driverDistance", driver.distance);
        event.put("driverEta", driver.eta);
        event.put("matchScore", driver.score);
        event.put("timestamp", System.currentTimeMillis());

        kafkaTemplate.send(TOPIC_RIDE_MATCHES, request.getRequestId().toString(), event);
    }

    /**
     * Internal class for scored driver candidates.
     */
    private record ScoredDriver(
            UUID driverId,
            double latitude,
            double longitude,
            double distance,
            int eta,
            BigDecimal rating,
            double score) {
    }
}
