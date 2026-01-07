package com.uber.spatial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GeoHash spatial indexing implementation.
 */
class GeoHashTest {

    // San Francisco coordinates
    private static final double SF_LAT = 37.7749;
    private static final double SF_LNG = -122.4194;

    // New York coordinates
    private static final double NY_LAT = 40.7128;
    private static final double NY_LNG = -74.0060;

    @Test
    @DisplayName("Should encode latitude/longitude to geohash")
    void testEncode() {
        String geohash = GeoHash.encode(SF_LAT, SF_LNG, 7);

        assertNotNull(geohash);
        assertEquals(7, geohash.length());
        assertTrue(geohash.startsWith("9q"));
    }

    @Test
    @DisplayName("Should decode geohash back to coordinates")
    void testDecode() {
        String geohash = GeoHash.encode(SF_LAT, SF_LNG, 7);
        double[] decoded = GeoHash.decode(geohash);

        // Should be within ~76m (precision 7) of original
        assertEquals(SF_LAT, decoded[0], 0.001);
        assertEquals(SF_LNG, decoded[1], 0.001);
    }

    @Test
    @DisplayName("Should return 9 neighbors (center + 8 surrounding)")
    void testGetNeighbors() {
        String geohash = GeoHash.encode(SF_LAT, SF_LNG, 6);
        String[] neighbors = GeoHash.getNeighbors(geohash);

        assertEquals(9, neighbors.length);
        assertEquals(geohash, neighbors[0]); // Center is first
    }

    @Test
    @DisplayName("Should get bounding box for geohash")
    void testGetBoundingBox() {
        String geohash = GeoHash.encode(SF_LAT, SF_LNG, 6);
        double[] bbox = GeoHash.getBoundingBox(geohash);

        assertEquals(4, bbox.length);
        // min lat/lng should be less than max
        assertTrue(bbox[0] < bbox[2]); // minLat < maxLat
        assertTrue(bbox[1] < bbox[3]); // minLng < maxLng

        // Original point should be within bounding box
        assertTrue(SF_LAT >= bbox[0] && SF_LAT <= bbox[2]);
        assertTrue(SF_LNG >= bbox[1] && SF_LNG <= bbox[3]);
    }

    @Test
    @DisplayName("Should determine precision based on radius")
    void testGetPrecisionForRadius() {
        assertEquals(4, GeoHash.getPrecisionForRadius(20000)); // 20km
        assertEquals(5, GeoHash.getPrecisionForRadius(5000)); // 5km
        assertEquals(6, GeoHash.getPrecisionForRadius(2000)); // 2km
        assertEquals(7, GeoHash.getPrecisionForRadius(500)); // 500m
        assertEquals(8, GeoHash.getPrecisionForRadius(100)); // 100m
    }

    @Test
    @DisplayName("Should reject invalid coordinates")
    void testInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> GeoHash.encode(91.0, 0.0, 7)); // Invalid latitude

        assertThrows(IllegalArgumentException.class, () -> GeoHash.encode(0.0, 181.0, 7)); // Invalid longitude
    }

    @Test
    @DisplayName("Should get cells for radius search")
    void testGetCellsForRadius() {
        String[] cells = GeoHash.getCellsForRadius(SF_LAT, SF_LNG, 2000);

        assertTrue(cells.length >= 1);
        assertTrue(cells.length <= 9); // At most center + 8 neighbors
    }

    @Test
    @DisplayName("Different locations should have different geohashes")
    void testDifferentLocations() {
        String sfHash = GeoHash.encode(SF_LAT, SF_LNG, 7);
        String nyHash = GeoHash.encode(NY_LAT, NY_LNG, 7);

        assertNotEquals(sfHash, nyHash);
    }

    @Test
    @DisplayName("Nearby points should share geohash prefix")
    void testNearbyPointsSharePrefix() {
        // Two points ~100m apart in SF
        String hash1 = GeoHash.encode(37.7749, -122.4194, 7);
        String hash2 = GeoHash.encode(37.7750, -122.4195, 7);

        // Should share at least first 5 characters
        assertTrue(hash1.substring(0, 5).equals(hash2.substring(0, 5)));
    }
}
