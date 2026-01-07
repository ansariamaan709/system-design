package com.uber.spatial;

/**
 * Geohash implementation for spatial indexing.
 * 
 * Geohash encodes a geographic location into a short string of letters and
 * digits.
 * It's a hierarchical spatial data structure which subdivides space into
 * buckets of grid shape.
 * 
 * Precision levels:
 * - 1: ±2500km
 * - 2: ±630km
 * - 3: ±78km
 * - 4: ±20km
 * - 5: ±2.4km
 * - 6: ±610m
 * - 7: ±76m (default for Uber)
 * - 8: ±19m
 * - 9: ±2.4m
 * 
 * Example: "9q8yyk8" represents San Francisco area
 */
public class GeoHash {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final int[] BITS = { 16, 8, 4, 2, 1 };

    // Default precision for Uber-like applications
    public static final int DEFAULT_PRECISION = 7;

    /**
     * Encode latitude and longitude into a geohash string.
     *
     * @param latitude  Latitude (-90 to 90)
     * @param longitude Longitude (-180 to 180)
     * @param precision Number of characters in the geohash (1-12)
     * @return Geohash string
     */
    public static String encode(double latitude, double longitude, int precision) {
        validateCoordinates(latitude, longitude);

        if (precision < 1 || precision > 12) {
            throw new IllegalArgumentException("Precision must be between 1 and 12");
        }

        double[] latRange = { -90.0, 90.0 };
        double[] lonRange = { -180.0, 180.0 };

        StringBuilder geohash = new StringBuilder();
        boolean isEven = true;
        int bit = 0;
        int ch = 0;

        while (geohash.length() < precision) {
            double mid;
            if (isEven) {
                mid = (lonRange[0] + lonRange[1]) / 2;
                if (longitude >= mid) {
                    ch |= BITS[bit];
                    lonRange[0] = mid;
                } else {
                    lonRange[1] = mid;
                }
            } else {
                mid = (latRange[0] + latRange[1]) / 2;
                if (latitude >= mid) {
                    ch |= BITS[bit];
                    latRange[0] = mid;
                } else {
                    latRange[1] = mid;
                }
            }

            isEven = !isEven;

            if (bit < 4) {
                bit++;
            } else {
                geohash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }

        return geohash.toString();
    }

    /**
     * Encode with default precision.
     */
    public static String encode(double latitude, double longitude) {
        return encode(latitude, longitude, DEFAULT_PRECISION);
    }

    /**
     * Decode a geohash string back to latitude and longitude.
     *
     * @param geohash The geohash string
     * @return Array of [latitude, longitude]
     */
    public static double[] decode(String geohash) {
        if (geohash == null || geohash.isEmpty()) {
            throw new IllegalArgumentException("Geohash cannot be null or empty");
        }

        double[] latRange = { -90.0, 90.0 };
        double[] lonRange = { -180.0, 180.0 };
        boolean isEven = true;

        for (char c : geohash.toLowerCase().toCharArray()) {
            int cd = BASE32.indexOf(c);
            if (cd == -1) {
                throw new IllegalArgumentException("Invalid geohash character: " + c);
            }

            for (int mask : BITS) {
                if (isEven) {
                    if ((cd & mask) != 0) {
                        lonRange[0] = (lonRange[0] + lonRange[1]) / 2;
                    } else {
                        lonRange[1] = (lonRange[0] + lonRange[1]) / 2;
                    }
                } else {
                    if ((cd & mask) != 0) {
                        latRange[0] = (latRange[0] + latRange[1]) / 2;
                    } else {
                        latRange[1] = (latRange[0] + latRange[1]) / 2;
                    }
                }
                isEven = !isEven;
            }
        }

        double latitude = (latRange[0] + latRange[1]) / 2;
        double longitude = (lonRange[0] + lonRange[1]) / 2;

        return new double[] { latitude, longitude };
    }

    /**
     * Get the bounding box for a geohash.
     *
     * @param geohash The geohash string
     * @return Array of [minLat, minLon, maxLat, maxLon]
     */
    public static double[] getBoundingBox(String geohash) {
        double[] latRange = { -90.0, 90.0 };
        double[] lonRange = { -180.0, 180.0 };
        boolean isEven = true;

        for (char c : geohash.toLowerCase().toCharArray()) {
            int cd = BASE32.indexOf(c);

            for (int mask : BITS) {
                if (isEven) {
                    double mid = (lonRange[0] + lonRange[1]) / 2;
                    if ((cd & mask) != 0) {
                        lonRange[0] = mid;
                    } else {
                        lonRange[1] = mid;
                    }
                } else {
                    double mid = (latRange[0] + latRange[1]) / 2;
                    if ((cd & mask) != 0) {
                        latRange[0] = mid;
                    } else {
                        latRange[1] = mid;
                    }
                }
                isEven = !isEven;
            }
        }

        return new double[] { latRange[0], lonRange[0], latRange[1], lonRange[1] };
    }

    /**
     * Get all 8 neighboring geohashes plus the center.
     *
     * @param geohash The center geohash
     * @return Array of 9 geohashes (center + 8 neighbors)
     */
    public static String[] getNeighbors(String geohash) {
        String[] neighbors = new String[9];
        neighbors[0] = geohash; // Center

        neighbors[1] = getAdjacent(geohash, Direction.NORTH);
        neighbors[2] = getAdjacent(geohash, Direction.SOUTH);
        neighbors[3] = getAdjacent(geohash, Direction.EAST);
        neighbors[4] = getAdjacent(geohash, Direction.WEST);
        neighbors[5] = getAdjacent(neighbors[1], Direction.EAST); // NE
        neighbors[6] = getAdjacent(neighbors[1], Direction.WEST); // NW
        neighbors[7] = getAdjacent(neighbors[2], Direction.EAST); // SE
        neighbors[8] = getAdjacent(neighbors[2], Direction.WEST); // SW

        return neighbors;
    }

    /**
     * Get the geohash cells needed to cover a radius around a point.
     *
     * @param latitude     Center latitude
     * @param longitude    Center longitude
     * @param radiusMeters Search radius in meters
     * @return Array of geohash strings covering the area
     */
    public static String[] getCellsForRadius(double latitude, double longitude, double radiusMeters) {
        // Determine appropriate precision based on radius
        int precision = getPrecisionForRadius(radiusMeters);
        String centerHash = encode(latitude, longitude, precision);

        // For small radii, single cell might be enough
        if (radiusMeters < getCellSizeMeters(precision) / 4) {
            return new String[] { centerHash };
        }

        // For larger radii, include neighbors
        return getNeighbors(centerHash);
    }

    /**
     * Determine the best precision for a given radius.
     */
    public static int getPrecisionForRadius(double radiusMeters) {
        // Map radius to appropriate precision
        // Ensure the cell size is smaller than the search radius
        if (radiusMeters >= 20000)
            return 4; // 20km+
        if (radiusMeters >= 5000)
            return 5; // 5-20km
        if (radiusMeters >= 1000)
            return 6; // 1-5km
        if (radiusMeters >= 200)
            return 7; // 200m-1km
        if (radiusMeters >= 50)
            return 8; // 50-200m
        return 9; // <50m
    }

    /**
     * Get approximate cell size in meters for a precision level.
     */
    public static double getCellSizeMeters(int precision) {
        // Approximate cell sizes (varies by latitude)
        return switch (precision) {
            case 1 -> 5000000; // 5000km
            case 2 -> 1260000; // 1260km
            case 3 -> 156000; // 156km
            case 4 -> 40000; // 40km
            case 5 -> 4900; // 4.9km
            case 6 -> 1200; // 1.2km
            case 7 -> 152; // 152m
            case 8 -> 38; // 38m
            case 9 -> 4.8; // 4.8m
            default -> 152;
        };
    }

    private enum Direction {
        NORTH, SOUTH, EAST, WEST
    }

    private static final String[][] NEIGHBORS = {
            { "p0r21436x8zb9dcf5h7kjnmqesgutwvy", "bc01fg45238967deuvhjyznpkmstqrwx" }, // NORTH (odd, even)
            { "14365h7k9dcfesgujnmqp0r2twvyx8zb", "238967debc01teletext45teletext67hjuv" }, // SOUTH
            { "bc01fg45238967deuvhjyznpkmstqrwx", "p0r21436x8zb9dcf5h7kjnmqesgutwvy" }, // EAST
            { "238967debc01fg45uvhjyznpkmstqrwx", "14365h7k9dcfesgujnmqp0r2twvyx8zb" } // WEST
    };

    private static final String[][] BORDERS = {
            { "prxz", "bcfguvyz" }, // NORTH
            { "028b", "0145hjnp" }, // SOUTH
            { "bcfguvyz", "prxz" }, // EAST
            { "0145hjnp", "028b" } // WEST
    };

    private static String getAdjacent(String geohash, Direction direction) {
        if (geohash == null || geohash.isEmpty()) {
            return geohash;
        }

        geohash = geohash.toLowerCase();
        char lastChar = geohash.charAt(geohash.length() - 1);
        String parent = geohash.substring(0, geohash.length() - 1);
        int type = geohash.length() % 2;
        int dirIndex = direction.ordinal();

        // Check if we need to recurse to parent
        if (BORDERS[dirIndex][type].indexOf(lastChar) != -1 && !parent.isEmpty()) {
            parent = getAdjacent(parent, direction);
        }

        int charIndex = NEIGHBORS[dirIndex][type].indexOf(lastChar);
        if (charIndex == -1) {
            charIndex = 0;
        }

        return parent + BASE32.charAt(charIndex);
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }
}
