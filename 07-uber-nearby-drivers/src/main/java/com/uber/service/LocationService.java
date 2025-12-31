package com.uber.service;

import com.uber.dto.BatchLocationUpdateRequest;
import com.uber.dto.BatchLocationUpdateResponse;
import com.uber.dto.LocationUpdateRequest;
import com.uber.entity.DriverLocation;
import com.uber.entity.DriverStatus;
import com.uber.repository.DriverLocationRepository;
import com.uber.spatial.GeoHash;
import com.uber.spatial.H3Index;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.uber.config.KafkaConfig.TOPIC_LOCATION_UPDATES;

/**
 * Service for handling driver location updates.
 * 
 * Hot path: Redis for real-time location storage
 * Cold path: PostgreSQL for historical data (via Kafka)
 * 
 * Redis keys:
 * - drivers:geo:{cityId}  -> GEOADD for spatial queries
 * - driver:location:{id}  -> HSET for full location data
 * - driver:status:{id}    -> String for quick status check
 */
@Service
@Slf4j
public class LocationService {
    
    private static final String GEO_KEY_PREFIX = "drivers:geo:";
    private static final String LOCATION_KEY_PREFIX = "driver:location:";
    private static final String STATUS_KEY_PREFIX = "driver:status:";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final DriverLocationRepository locationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    private final Counter locationUpdatesCounter;
    private final Counter locationUpdateErrorsCounter;
    private final Timer locationUpdateTimer;
    
    @Value("${uber.location.stale-threshold-ms:30000}")
    private long staleThresholdMs;
    
    @Value("${uber.location.max-speed-kmh:200}")
    private double maxSpeedKmh;
    
    @Value("${uber.location.geohash-precision:7}")
    private int geohashPrecision;
    
    @Value("${uber.location.h3-resolution:9}")
    private int h3Resolution;
    
    public LocationService(
            RedisTemplate<String, Object> redisTemplate,
            DriverLocationRepository locationRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.locationRepository = locationRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.locationUpdatesCounter = Counter.builder("location.updates.total")
                .description("Total location updates processed")
                .register(meterRegistry);
        
        this.locationUpdateErrorsCounter = Counter.builder("location.updates.errors")
                .description("Location update errors")
                .register(meterRegistry);
        
        this.locationUpdateTimer = Timer.builder("location.updates.latency")
                .description("Location update processing time")
                .register(meterRegistry);
    }
    
    /**
     * Update a single driver's location.
     * This is the primary hot path for location ingestion.
     */
    public void updateLocation(UUID driverId, String cityId, LocationUpdateRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Validate timestamp (reject stale updates)
            if (request.getTimestamp() != null) {
                long age = System.currentTimeMillis() - request.getTimestamp();
                if (age > staleThresholdMs) {
                    log.debug("Rejecting stale location update for driver {}: {}ms old", driverId, age);
                    return;
                }
            }
            
            // Calculate spatial indexes
            String geohash = GeoHash.encode(request.getLatitude(), request.getLongitude(), geohashPrecision);
            long h3Index = H3Index.latLngToCell(request.getLatitude(), request.getLongitude(), h3Resolution);
            
            // Build location data
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("driverId", driverId.toString());
            locationData.put("latitude", request.getLatitude());
            locationData.put("longitude", request.getLongitude());
            locationData.put("geohash", geohash);
            locationData.put("h3Index", h3Index);
            locationData.put("heading", request.getHeading());
            locationData.put("speed", request.getSpeed());
            locationData.put("accuracy", request.getAccuracy());
            locationData.put("timestamp", System.currentTimeMillis());
            
            // Update Redis GEO index for spatial queries
            String geoKey = GEO_KEY_PREFIX + cityId;
            redisTemplate.opsForGeo().add(geoKey, 
                    new Point(request.getLongitude(), request.getLatitude()), 
                    driverId.toString());
            
            // Set TTL on geo key entries (30 seconds)
            redisTemplate.expire(geoKey, Duration.ofSeconds(30));
            
            // Store full location data
            String locationKey = LOCATION_KEY_PREFIX + driverId;
            redisTemplate.opsForHash().putAll(locationKey, locationData);
            redisTemplate.expire(locationKey, Duration.ofSeconds(30));
            
            locationUpdatesCounter.increment();
            
            // Async: Publish to Kafka for persistence and analytics
            publishLocationUpdate(driverId, cityId, locationData);
            
        } catch (Exception e) {
            locationUpdateErrorsCounter.increment();
            log.error("Failed to update location for driver {}: {}", driverId, e.getMessage());
            throw e;
        } finally {
            sample.stop(locationUpdateTimer);
        }
    }
    
    /**
     * Batch update multiple driver locations.
     * Used for high-throughput ingestion scenarios.
     */
    public BatchLocationUpdateResponse batchUpdateLocations(String cityId, BatchLocationUpdateRequest request) {
        int processed = 0;
        int failed = 0;
        
        for (BatchLocationUpdateRequest.DriverLocationUpdate update : request.getLocations()) {
            try {
                LocationUpdateRequest locationRequest = LocationUpdateRequest.builder()
                        .latitude(update.getLat())
                        .longitude(update.getLng())
                        .heading(update.getHeading())
                        .speed(update.getSpeed())
                        .accuracy(update.getAccuracy())
                        .timestamp(update.getTs())
                        .build();
                
                updateLocation(update.getDriverId(), cityId, locationRequest);
                processed++;
                
            } catch (Exception e) {
                failed++;
                log.warn("Failed to update location for driver {} in batch: {}", 
                        update.getDriverId(), e.getMessage());
            }
        }
        
        if (failed == 0) {
            return BatchLocationUpdateResponse.success(processed);
        } else {
            return BatchLocationUpdateResponse.partial(processed, failed);
        }
    }
    
    /**
     * Update driver status (AVAILABLE, BUSY, OFFLINE).
     */
    public void updateDriverStatus(UUID driverId, DriverStatus status) {
        String statusKey = STATUS_KEY_PREFIX + driverId;
        redisTemplate.opsForValue().set(statusKey, status.name());
        
        if (status == DriverStatus.OFFLINE) {
            // Remove from geo index when offline
            // Note: In production, we'd need to know the city to remove from correct key
            redisTemplate.delete(LOCATION_KEY_PREFIX + driverId);
        }
        
        log.debug("Updated driver {} status to {}", driverId, status);
    }
    
    /**
     * Get current location of a driver from Redis.
     */
    public DriverLocation getDriverLocation(UUID driverId) {
        String locationKey = LOCATION_KEY_PREFIX + driverId;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(locationKey);
        
        if (data.isEmpty()) {
            return null;
        }
        
        return DriverLocation.builder()
                .driverId(driverId)
                .latitude(Double.parseDouble(data.get("latitude").toString()))
                .longitude(Double.parseDouble(data.get("longitude").toString()))
                .geohash((String) data.get("geohash"))
                .h3Index(Long.parseLong(data.get("h3Index").toString()))
                .heading(data.get("heading") != null ? Short.parseShort(data.get("heading").toString()) : null)
                .speed(data.get("speed") != null ? Float.parseFloat(data.get("speed").toString()) : null)
                .accuracy(data.get("accuracy") != null ? Float.parseFloat(data.get("accuracy").toString()) : null)
                .updatedAt(Instant.ofEpochMilli(Long.parseLong(data.get("timestamp").toString())))
                .build();
    }
    
    /**
     * Get driver status from Redis.
     */
    public DriverStatus getDriverStatus(UUID driverId) {
        String statusKey = STATUS_KEY_PREFIX + driverId;
        Object status = redisTemplate.opsForValue().get(statusKey);
        
        if (status == null) {
            return DriverStatus.OFFLINE;
        }
        
        return DriverStatus.valueOf(status.toString());
    }
    
    /**
     * Publish location update to Kafka for async processing.
     */
    @Async
    protected void publishLocationUpdate(UUID driverId, String cityId, Map<String, Object> locationData) {
        try {
            // Use geohash prefix as partition key for locality
            String partitionKey = locationData.get("geohash").toString().substring(0, 4);
            
            Map<String, Object> event = new HashMap<>(locationData);
            event.put("cityId", cityId);
            event.put("eventType", "LOCATION_UPDATE");
            
            kafkaTemplate.send(TOPIC_LOCATION_UPDATES, partitionKey, event);
            
        } catch (Exception e) {
            log.warn("Failed to publish location update to Kafka: {}", e.getMessage());
            // Don't fail the main operation for Kafka issues
        }
    }
    
    /**
     * Persist location to PostgreSQL (called by Kafka consumer).
     */
    public void persistLocation(DriverLocation location) {
        try {
            locationRepository.save(location);
        } catch (Exception e) {
            log.error("Failed to persist location to PostgreSQL: {}", e.getMessage());
        }
    }
}
