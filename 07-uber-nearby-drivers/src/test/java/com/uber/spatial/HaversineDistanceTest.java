package com.uber.spatial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Haversine distance calculations.
 */
class HaversineDistanceTest {

    // San Francisco coordinates
    private static final double SF_LAT = 37.7749;
    private static final double SF_LNG = -122.4194;
    
    // Oakland (across the bay, ~13km)
    private static final double OAK_LAT = 37.8044;
    private static final double OAK_LNG = -122.2712;
    
    // New York coordinates
    private static final double NY_LAT = 40.7128;
    private static final double NY_LNG = -74.0060;

    @Test
    @DisplayName("Should calculate distance in meters")
    void testCalculateMeters() {
        double distance = HaversineDistance.calculateMeters(SF_LAT, SF_LNG, OAK_LAT, OAK_LNG);
        
        // SF to Oakland should be approximately 13-15km
        assertTrue(distance > 12000 && distance < 16000, 
            "Distance should be approximately 13-15km but was " + distance);
    }

    @Test
    @DisplayName("Should calculate distance in kilometers")
    void testCalculateKilometers() {
        double distance = HaversineDistance.calculateKilometers(SF_LAT, SF_LNG, NY_LAT, NY_LNG);
        
        // SF to NY should be approximately 4,100-4,200km
        assertTrue(distance > 4000 && distance < 4300,
            "Distance should be approximately 4100km but was " + distance);
    }

    @Test
    @DisplayName("Should return zero for same point")
    void testSamePoint() {
        double distance = HaversineDistance.calculateMeters(SF_LAT, SF_LNG, SF_LAT, SF_LNG);
        assertEquals(0, distance, 0.001);
    }

    @Test
    @DisplayName("Should calculate bearing between points")
    void testCalculateBearing() {
        // Bearing from SF to NY should be roughly northeast (~72 degrees)
        double bearing = HaversineDistance.calculateBearing(SF_LAT, SF_LNG, NY_LAT, NY_LNG);
        
        assertTrue(bearing >= 0 && bearing < 360);
        assertTrue(bearing > 50 && bearing < 90, 
            "Bearing to NY should be roughly northeast but was " + bearing);
    }

    @Test
    @DisplayName("Should calculate destination point from bearing and distance")
    void testCalculateDestination() {
        // Move 1km north from SF
        double[] destination = HaversineDistance.calculateDestination(SF_LAT, SF_LNG, 0, 1000);
        
        // Latitude should increase (going north)
        assertTrue(destination[0] > SF_LAT);
        // Longitude should stay roughly the same
        assertEquals(SF_LNG, destination[1], 0.01);
        
        // Verify distance is approximately 1km
        double distance = HaversineDistance.calculateMeters(
            SF_LAT, SF_LNG, destination[0], destination[1]);
        assertEquals(1000, distance, 10);
    }

    @Test
    @DisplayName("Should calculate bounding box")
    void testCalculateBoundingBox() {
        double[] bbox = HaversineDistance.calculateBoundingBox(SF_LAT, SF_LNG, 1000);
        
        // Should return [minLat, minLon, maxLat, maxLon]
        assertEquals(4, bbox.length);
        
        // Min should be less than max
        assertTrue(bbox[0] < bbox[2]); // minLat < maxLat
        assertTrue(bbox[1] < bbox[3]); // minLon < maxLon
        
        // Original point should be at center
        double centerLat = (bbox[0] + bbox[2]) / 2;
        double centerLon = (bbox[1] + bbox[3]) / 2;
        assertEquals(SF_LAT, centerLat, 0.01);
        assertEquals(SF_LNG, centerLon, 0.01);
    }

    @Test
    @DisplayName("Should check if point is within radius")
    void testIsWithinRadius() {
        // Oakland should be within 15km of SF
        assertTrue(HaversineDistance.isWithinRadius(SF_LAT, SF_LNG, OAK_LAT, OAK_LNG, 15000));
        
        // Oakland should NOT be within 5km of SF
        assertFalse(HaversineDistance.isWithinRadius(SF_LAT, SF_LNG, OAK_LAT, OAK_LNG, 5000));
        
        // NY should NOT be within 100km of SF
        assertFalse(HaversineDistance.isWithinRadius(SF_LAT, SF_LNG, NY_LAT, NY_LNG, 100000));
    }

    @Test
    @DisplayName("Should calculate midpoint between two points")
    void testCalculateMidpoint() {
        double[] midpoint = HaversineDistance.calculateMidpoint(SF_LAT, SF_LNG, OAK_LAT, OAK_LNG);
        
        // Midpoint latitude should be between SF and Oakland latitudes
        assertTrue(midpoint[0] > Math.min(SF_LAT, OAK_LAT));
        assertTrue(midpoint[0] < Math.max(SF_LAT, OAK_LAT));
        
        // Midpoint longitude should be between SF and Oakland longitudes
        assertTrue(midpoint[1] > Math.min(SF_LNG, OAK_LNG));
        assertTrue(midpoint[1] < Math.max(SF_LNG, OAK_LNG));
    }

    @Test
    @DisplayName("Should estimate ETA in seconds")
    void testEstimateEtaSeconds() {
        // 1km at 25km/h = 144 seconds
        // SF to Oakland (~13km) at 25km/h should be ~30-40 minutes
        int eta = HaversineDistance.estimateEtaSeconds(SF_LAT, SF_LNG, OAK_LAT, OAK_LNG);
        
        assertTrue(eta > 1500 && eta < 2500, 
            "ETA should be approximately 30-40 minutes but was " + eta + " seconds");
    }

    @Test
    @DisplayName("Should estimate ETA with custom speed")
    void testEstimateEtaSecondsWithSpeed() {
        // 13km at 60km/h = ~13 minutes = ~780 seconds
        int eta = HaversineDistance.estimateEtaSeconds(SF_LAT, SF_LNG, OAK_LAT, OAK_LNG, 60.0);
        
        assertTrue(eta > 600 && eta < 1000,
            "ETA at 60km/h should be approximately 13 minutes but was " + eta + " seconds");
    }
}
