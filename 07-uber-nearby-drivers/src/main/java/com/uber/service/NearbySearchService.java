package com.uber.service;

import com.uber.dto.NearbyDriver;
import com.uber.dto.NearbyDriversResponse;
import com.uber.entity.DriverStatus;
import com.uber.entity.VehicleType;
import com.uber.spatial.GeoHash;
import com.uber.spatial.H3Index;
import com.uber.spatial.HaversineDistance;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for finding nearby available drivers.
 * 
 * Uses Redis GEO commands for spatial queries:
 * - GEORADIUS: Find members within radius
 * - GEOPOS: Get position of a member
 * - GEODIST: Calculate distance between members
 * 
 * Search strategy:
 * 1. Use Redis GEORADIUS for initial candidate set
 * 2. Filter by availability status
 * 3. Filter by vehicle type if specified
 * 4. Calculate exact distances using Haversine
 * 5. Sort by distance and return top K
 */
@Service
@Slf4j
public class NearbySearchService {
    
    private static final String GEO_KEY_PREFIX = "drivers:geo:";
    private static final String LOCATION_KEY_PREFIX = "driver:location:";
    private static final String STATUS_KEY_PREFIX = "driver:status:";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    
    private final Counter nearbySearchCounter;
    private final Timer nearbySearchTimer;
    
    @Value("${uber.location.default-search-radius-meters:5000}")
    private int defaultSearchRadius;
    
    @Value("${uber.location.max-search-radius-meters:20000}")
    private int maxSearchRadius;
    
    @Value("${uber.location.default-driver-limit:20}")
    private int defaultDriverLimit;
    
    @Value("${uber.location.max-driver-limit:50}")
    private int maxDriverLimit;
    
    @Value("${uber.location.location-fuzz-meters:50}")
    private int locationFuzzMeters;
    
    @Value("${uber.location.coordinate-precision:5}")
    private int coordinatePrecision;
    
    public NearbySearchService(RedisTemplate<String, Object> redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        
        this.nearbySearchCounter = Counter.builder("nearby.search.total")
                .description("Total nearby driver searches")
                .register(meterRegistry);
        
        this.nearbySearchTimer = Timer.builder("nearby.search.latency")
                .description("Nearby search processing time")
                .register(meterRegistry);
    }
    
    /**
     * Find nearby available drivers within a radius.
     *
     * @param cityId      City identifier for geo key
     * @param latitude    Search center latitude
     * @param longitude   Search center longitude
     * @param radiusMeters Search radius in meters
     * @param limit       Maximum number of drivers to return
     * @param vehicleType Optional vehicle type filter
     * @return NearbyDriversResponse with list of nearby drivers
     */
    public NearbyDriversResponse findNearbyDrivers(
            String cityId,
            double latitude,
            double longitude,
            Integer radiusMeters,
            Integer limit,
            VehicleType vehicleType) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();
        
        try {
            // Apply defaults and limits
            int radius = radiusMeters != null ? Math.min(radiusMeters, maxSearchRadius) : defaultSearchRadius;
            int maxResults = limit != null ? Math.min(limit, maxDriverLimit) : defaultDriverLimit;
            
            String geoKey = GEO_KEY_PREFIX + cityId;
            
            // Use Redis GEORADIUS to find candidates
            GeoResults<RedisGeoCommands.GeoLocation<Object>> geoResults = redisTemplate.opsForGeo()
                    .radius(geoKey,
                            new Circle(new Point(longitude, latitude), new Distance(radius, Metrics.METERS)),
                            RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                    .includeCoordinates()
                                    .includeDistance()
                                    .sortAscending()
                                    .limit(maxResults * 2)); // Get extra for filtering
            
            if (geoResults == null || geoResults.getContent().isEmpty()) {
                nearbySearchCounter.increment();
                return NearbyDriversResponse.empty(radius, System.currentTimeMillis() - startTime);
            }
            
            // Process results
            List<NearbyDriver> nearbyDrivers = new ArrayList<>();
            
            for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : geoResults.getContent()) {
                String driverId = result.getContent().getName().toString();
                UUID driverUuid = UUID.fromString(driverId);
                
                // Check driver status
                if (!isDriverAvailable(driverUuid)) {
                    continue;
                }
                
                // Get full driver data
                Map<Object, Object> locationData = redisTemplate.opsForHash()
                        .entries(LOCATION_KEY_PREFIX + driverId);
                
                if (locationData.isEmpty()) {
                    continue;
                }
                
                // Filter by vehicle type if specified
                if (vehicleType != null) {
                    String driverVehicleType = (String) locationData.get("vehicleType");
                    if (driverVehicleType == null || !driverVehicleType.equals(vehicleType.name())) {
                        continue;
                    }
                }
                
                Point point = result.getContent().getPoint();
                double distance = result.getDistance().getValue();
                
                // Apply location fuzzing for privacy
                double fuzzedLat = fuzzCoordinate(point.getY());
                double fuzzedLng = fuzzCoordinate(point.getX());
                
                // Get driver metadata
                Integer heading = locationData.get("heading") != null 
                        ? Integer.parseInt(locationData.get("heading").toString()) 
                        : 0;
                
                BigDecimal rating = locationData.get("rating") != null 
                        ? new BigDecimal(locationData.get("rating").toString())
                        : BigDecimal.valueOf(5.0);
                
                VehicleType type = locationData.get("vehicleType") != null
                        ? VehicleType.valueOf(locationData.get("vehicleType").toString())
                        : VehicleType.UBERX;
                
                nearbyDrivers.add(NearbyDriver.from(
                        driverUuid,
                        fuzzedLat,
                        fuzzedLng,
                        distance,
                        type,
                        rating,
                        heading));
                
                if (nearbyDrivers.size() >= maxResults) {
                    break;
                }
            }
            
            nearbySearchCounter.increment();
            long searchTime = System.currentTimeMillis() - startTime;
            
            log.debug("Found {} nearby drivers in {}ms (radius={}m)", 
                    nearbyDrivers.size(), searchTime, radius);
            
            return NearbyDriversResponse.of(nearbyDrivers, radius, searchTime);
            
        } finally {
            sample.stop(nearbySearchTimer);
        }
    }
    
    /**
     * Find nearby drivers using H3 k-ring search.
     * This is an alternative approach using Uber's H3 system.
     */
    public NearbyDriversResponse findNearbyDriversH3(
            String cityId,
            double latitude,
            double longitude,
            Integer radiusMeters,
            Integer limit,
            VehicleType vehicleType) {
        
        long startTime = System.currentTimeMillis();
        
        int radius = radiusMeters != null ? radiusMeters : defaultSearchRadius;
        int maxResults = limit != null ? Math.min(limit, maxDriverLimit) : defaultDriverLimit;
        
        // Get H3 cells covering the search radius
        int resolution = H3Index.getResolutionForRadius(radius);
        List<Long> searchCells = H3Index.getCellsForRadius(latitude, longitude, radius, resolution);
        
        // This would query a different Redis structure organized by H3 cells
        // For now, fall back to the standard geo search
        log.debug("H3 search would cover {} cells at resolution {}", searchCells.size(), resolution);
        
        return findNearbyDrivers(cityId, latitude, longitude, radiusMeters, limit, vehicleType);
    }
    
    /**
     * Expand search if initial radius returns too few drivers.
     */
    public NearbyDriversResponse findNearbyDriversWithExpansion(
            String cityId,
            double latitude,
            double longitude,
            int minDrivers,
            VehicleType vehicleType) {
        
        int[] radiusProgression = {2000, 5000, 10000, 15000, 20000};
        
        for (int radius : radiusProgression) {
            NearbyDriversResponse response = findNearbyDrivers(
                    cityId, latitude, longitude, radius, null, vehicleType);
            
            if (response.getTotalFound() >= minDrivers) {
                return response;
            }
        }
        
        // Return whatever we found at max radius
        return findNearbyDrivers(cityId, latitude, longitude, maxSearchRadius, null, vehicleType);
    }
    
    /**
     * Check if a driver is currently available.
     */
    private boolean isDriverAvailable(UUID driverId) {
        String statusKey = STATUS_KEY_PREFIX + driverId;
        Object status = redisTemplate.opsForValue().get(statusKey);
        
        if (status == null) {
            // If no status, assume available (they have a location update)
            return true;
        }
        
        return DriverStatus.AVAILABLE.name().equals(status.toString());
    }
    
    /**
     * Apply fuzzing to coordinates for privacy.
     * Adds random offset within configured meters.
     */
    private double fuzzCoordinate(double coordinate) {
        if (locationFuzzMeters <= 0) {
            return roundCoordinate(coordinate);
        }
        
        // Random offset: approximately ±fuzzMeters
        // 1 degree latitude ≈ 111km, so fuzzMeters/111000 degrees
        double fuzzDegrees = locationFuzzMeters / 111000.0;
        double offset = (Math.random() - 0.5) * 2 * fuzzDegrees;
        
        return roundCoordinate(coordinate + offset);
    }
    
    /**
     * Round coordinate to configured precision.
     */
    private double roundCoordinate(double coordinate) {
        double factor = Math.pow(10, coordinatePrecision);
        return Math.round(coordinate * factor) / factor;
    }
    
    /**
     * Calculate distance between two points using Haversine formula.
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        return HaversineDistance.calculateMeters(lat1, lon1, lat2, lon2);
    }
    
    /**
     * Estimate ETA from one point to another.
     */
    public int estimateEta(double fromLat, double fromLon, double toLat, double toLon) {
        return HaversineDistance.estimateEtaSeconds(fromLat, fromLon, toLat, toLon);
    }
}
