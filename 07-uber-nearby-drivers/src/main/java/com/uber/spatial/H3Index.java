package com.uber.spatial;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

/**
 * H3 Index wrapper for Uber's hexagonal hierarchical spatial index.
 * 
 * H3 is Uber's open-source hexagonal hierarchical geospatial indexing system.
 * It partitions the world into hexagonal cells at various resolutions.
 * 
 * Resolution levels:
 * - 0:  avg edge ~1107km
 * - 4:  avg edge ~22.6km
 * - 7:  avg edge ~1.22km
 * - 9:  avg edge ~174m  (commonly used for ride-hailing)
 * - 10: avg edge ~66m
 * - 11: avg edge ~25m
 * - 12: avg edge ~9.4m
 * - 15: avg edge ~0.5m
 * 
 * Benefits over Geohash:
 * - Uniform cell shapes (hexagons vs rectangles)
 * - No edge distortion at poles
 * - Consistent neighbor relationships (6 neighbors)
 * - Better for distance calculations
 */
@Slf4j
public class H3Index {
    
    private static final H3Core h3;
    
    // Default resolution for ride-hailing (174m edge)
    public static final int DEFAULT_RESOLUTION = 9;
    
    // Resolution for coarse city-level queries
    public static final int CITY_RESOLUTION = 4;
    
    // Resolution for precise pickup points
    public static final int PRECISE_RESOLUTION = 11;
    
    static {
        try {
            h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize H3 library", e);
        }
    }
    
    /**
     * Convert latitude/longitude to H3 index at default resolution.
     *
     * @param latitude  Latitude
     * @param longitude Longitude
     * @return H3 index as long
     */
    public static long latLngToCell(double latitude, double longitude) {
        return latLngToCell(latitude, longitude, DEFAULT_RESOLUTION);
    }
    
    /**
     * Convert latitude/longitude to H3 index at specified resolution.
     *
     * @param latitude   Latitude
     * @param longitude  Longitude
     * @param resolution H3 resolution (0-15)
     * @return H3 index as long
     */
    public static long latLngToCell(double latitude, double longitude, int resolution) {
        validateCoordinates(latitude, longitude);
        validateResolution(resolution);
        return h3.latLngToCell(latitude, longitude, resolution);
    }
    
    /**
     * Convert H3 index to latitude/longitude (center of hexagon).
     *
     * @param h3Index H3 index
     * @return Array of [latitude, longitude]
     */
    public static double[] cellToLatLng(long h3Index) {
        LatLng latLng = h3.cellToLatLng(h3Index);
        return new double[]{latLng.lat, latLng.lng};
    }
    
    /**
     * Convert H3 index to string representation.
     *
     * @param h3Index H3 index
     * @return Hex string representation
     */
    public static String h3ToString(long h3Index) {
        return h3.h3ToString(h3Index);
    }
    
    /**
     * Convert string H3 index to long.
     *
     * @param h3String Hex string representation
     * @return H3 index as long
     */
    public static long stringToH3(String h3String) {
        return h3.stringToH3(h3String);
    }
    
    /**
     * Get the resolution of an H3 index.
     *
     * @param h3Index H3 index
     * @return Resolution (0-15)
     */
    public static int getResolution(long h3Index) {
        return h3.getResolution(h3Index);
    }
    
    /**
     * Get all neighboring hexagons (k-ring) around a center cell.
     * k=0 returns just the center cell
     * k=1 returns center + 6 neighbors (7 total)
     * k=2 returns 19 total hexagons
     *
     * @param h3Index Center H3 index
     * @param k       Ring size (0 = center only)
     * @return List of H3 indexes in the k-ring
     */
    public static List<Long> gridDisk(long h3Index, int k) {
        return h3.gridDisk(h3Index, k);
    }
    
    /**
     * Get only the hexagons in the outer ring (hollow ring).
     *
     * @param h3Index Center H3 index
     * @param k       Ring number
     * @return List of H3 indexes in ring k
     */
    public static List<Long> gridRingUnsafe(long h3Index, int k) {
        try {
            return h3.gridRingUnsafe(h3Index, k);
        } catch (Exception e) {
            // Fall back to disk difference for pentagon cases
            if (k == 0) {
                return Collections.singletonList(h3Index);
            }
            Set<Long> ring = new HashSet<>(h3.gridDisk(h3Index, k));
            ring.removeAll(h3.gridDisk(h3Index, k - 1));
            return new ArrayList<>(ring);
        }
    }
    
    /**
     * Get immediate neighbors of a hexagon (6 neighbors).
     *
     * @param h3Index H3 index
     * @return List of 6 neighboring H3 indexes
     */
    public static List<Long> getNeighbors(long h3Index) {
        List<Long> disk = gridDisk(h3Index, 1);
        // Remove the center cell
        disk.remove(h3Index);
        return disk;
    }
    
    /**
     * Calculate the great-circle distance between two H3 cell centers.
     *
     * @param h3Index1 First H3 index
     * @param h3Index2 Second H3 index
     * @return Distance in meters
     */
    public static double gridDistance(long h3Index1, long h3Index2) {
        try {
            // Get the grid distance (number of cells apart)
            long gridDist = h3.gridDistance(h3Index1, h3Index2);
            // Convert to approximate meters based on resolution
            double edgeLength = getEdgeLengthMeters(getResolution(h3Index1));
            return gridDist * edgeLength * 1.5; // Approximate
        } catch (Exception e) {
            // Fall back to Haversine if grid distance fails
            double[] ll1 = cellToLatLng(h3Index1);
            double[] ll2 = cellToLatLng(h3Index2);
            return HaversineDistance.calculateMeters(ll1[0], ll1[1], ll2[0], ll2[1]);
        }
    }
    
    /**
     * Get the parent cell at a coarser resolution.
     *
     * @param h3Index       H3 index
     * @param parentResolution Target resolution (must be < current resolution)
     * @return Parent H3 index
     */
    public static long cellToParent(long h3Index, int parentResolution) {
        validateResolution(parentResolution);
        return h3.cellToParent(h3Index, parentResolution);
    }
    
    /**
     * Get all children cells at a finer resolution.
     *
     * @param h3Index         H3 index
     * @param childResolution Target resolution (must be > current resolution)
     * @return List of child H3 indexes
     */
    public static List<Long> cellToChildren(long h3Index, int childResolution) {
        validateResolution(childResolution);
        return h3.cellToChildren(h3Index, childResolution);
    }
    
    /**
     * Check if a cell is a pentagon (there are 12 pentagons at each resolution).
     *
     * @param h3Index H3 index
     * @return true if the cell is a pentagon
     */
    public static boolean isPentagon(long h3Index) {
        return h3.isPentagon(h3Index);
    }
    
    /**
     * Get cells covering a radius from a point using expanding rings.
     * Returns cells progressively until the radius is covered.
     *
     * @param latitude     Center latitude
     * @param longitude    Center longitude
     * @param radiusMeters Search radius in meters
     * @param resolution   H3 resolution
     * @return List of H3 indexes covering the radius
     */
    public static List<Long> getCellsForRadius(double latitude, double longitude, 
                                                double radiusMeters, int resolution) {
        long centerCell = latLngToCell(latitude, longitude, resolution);
        double edgeLength = getEdgeLengthMeters(resolution);
        
        // Calculate number of rings needed
        // Each ring adds approximately 1.5 * edgeLength to the radius
        int rings = (int) Math.ceil(radiusMeters / (edgeLength * 1.5)) + 1;
        
        return gridDisk(centerCell, rings);
    }
    
    /**
     * Get cells for radius with default resolution.
     */
    public static List<Long> getCellsForRadius(double latitude, double longitude, double radiusMeters) {
        return getCellsForRadius(latitude, longitude, radiusMeters, DEFAULT_RESOLUTION);
    }
    
    /**
     * Get the best resolution for a given radius.
     * Chooses resolution where cell size is appropriate for the search radius.
     *
     * @param radiusMeters Search radius
     * @return Recommended H3 resolution
     */
    public static int getResolutionForRadius(double radiusMeters) {
        // Target: cell edge should be roughly 1/3 to 1/4 of the search radius
        // This ensures good coverage without too many cells
        if (radiusMeters >= 50000) return 4;   // 50km+
        if (radiusMeters >= 10000) return 6;   // 10-50km
        if (radiusMeters >= 5000) return 7;    // 5-10km
        if (radiusMeters >= 2000) return 8;    // 2-5km
        if (radiusMeters >= 500) return 9;     // 500m-2km (most common)
        if (radiusMeters >= 100) return 10;    // 100-500m
        return 11;                              // <100m
    }
    
    /**
     * Get approximate edge length in meters for a resolution.
     *
     * @param resolution H3 resolution
     * @return Edge length in meters
     */
    public static double getEdgeLengthMeters(int resolution) {
        // Approximate average edge lengths
        return switch (resolution) {
            case 0 -> 1107712.591;
            case 1 -> 418676.0055;
            case 2 -> 158244.6558;
            case 3 -> 59810.85794;
            case 4 -> 22606.3794;
            case 5 -> 8544.408276;
            case 6 -> 3229.482772;
            case 7 -> 1220.629759;
            case 8 -> 461.3540555;
            case 9 -> 174.375668;
            case 10 -> 65.90780749;
            case 11 -> 24.9105614;
            case 12 -> 9.415526211;
            case 13 -> 3.559893033;
            case 14 -> 1.348574562;
            case 15 -> 0.509713273;
            default -> 174.375668; // Default to res 9
        };
    }
    
    /**
     * Get approximate cell area in square meters for a resolution.
     *
     * @param resolution H3 resolution
     * @return Cell area in square meters
     */
    public static double getCellAreaSqMeters(int resolution) {
        // Approximate average cell areas
        return switch (resolution) {
            case 0 -> 4250546848000000.0;
            case 1 -> 607220986000000.0;
            case 2 -> 86745854000000.0;
            case 3 -> 12392264862000.0;
            case 4 -> 1770323552000.0;
            case 5 -> 252903364000.0;
            case 6 -> 36129052000.0;
            case 7 -> 5161293360.0;
            case 8 -> 737327598.0;
            case 9 -> 105332513.0;
            case 10 -> 15047502.0;
            case 11 -> 2149643.0;
            case 12 -> 307092.0;
            case 13 -> 43870.0;
            case 14 -> 6267.0;
            case 15 -> 895.0;
            default -> 105332513.0; // Default to res 9
        };
    }
    
    /**
     * Compact a set of H3 indexes, combining them into larger cells where possible.
     * Useful for reducing storage when covering large areas.
     *
     * @param h3Indexes Set of H3 indexes
     * @return Compacted set of H3 indexes
     */
    public static List<Long> compact(Collection<Long> h3Indexes) {
        return h3.compactCells(h3Indexes);
    }
    
    /**
     * Uncompact a set of H3 indexes to a target resolution.
     *
     * @param h3Indexes  Compacted H3 indexes
     * @param resolution Target resolution
     * @return Uncompacted set of H3 indexes
     */
    public static List<Long> uncompact(Collection<Long> h3Indexes, int resolution) {
        return h3.uncompactCells(h3Indexes, resolution);
    }
    
    /**
     * Get the polygon boundary of an H3 cell.
     *
     * @param h3Index H3 index
     * @return List of [lat, lng] pairs forming the boundary
     */
    public static List<double[]> cellToBoundary(long h3Index) {
        List<LatLng> boundary = h3.cellToBoundary(h3Index);
        return boundary.stream()
                .map(ll -> new double[]{ll.lat, ll.lng})
                .toList();
    }
    
    private static void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }
    
    private static void validateResolution(int resolution) {
        if (resolution < 0 || resolution > 15) {
            throw new IllegalArgumentException("Resolution must be between 0 and 15");
        }
    }
}
