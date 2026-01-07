package com.uber.spatial;

/**
 * Haversine distance calculation for great-circle distances.
 * 
 * The Haversine formula calculates the shortest distance between two points
 * on a sphere (great-circle distance) given their latitudes and longitudes.
 * 
 * Accuracy: ~0.5% error due to Earth's ellipsoid shape
 * For higher accuracy, use Vincenty's formulae (slower).
 * 
 * Earth's radius:
 * - Mean radius: 6,371 km
 * - Equatorial radius: 6,378.137 km
 * - Polar radius: 6,356.752 km
 */
public class HaversineDistance {

    // Earth's mean radius in meters
    public static final double EARTH_RADIUS_METERS = 6_371_000;

    // Earth's mean radius in kilometers
    public static final double EARTH_RADIUS_KM = 6_371;

    /**
     * Calculate the distance between two points in meters.
     *
     * @param lat1 Latitude of point 1
     * @param lon1 Longitude of point 1
     * @param lat2 Latitude of point 2
     * @param lon2 Longitude of point 2
     * @return Distance in meters
     */
    public static double calculateMeters(double lat1, double lon1, double lat2, double lon2) {
        return calculate(lat1, lon1, lat2, lon2, EARTH_RADIUS_METERS);
    }

    /**
     * Calculate the distance between two points in kilometers.
     *
     * @param lat1 Latitude of point 1
     * @param lon1 Longitude of point 1
     * @param lat2 Latitude of point 2
     * @param lon2 Longitude of point 2
     * @return Distance in kilometers
     */
    public static double calculateKilometers(double lat1, double lon1, double lat2, double lon2) {
        return calculate(lat1, lon1, lat2, lon2, EARTH_RADIUS_KM);
    }

    /**
     * Calculate the distance between two points with custom Earth radius.
     * 
     * Formula:
     * a = sin²(Δφ/2) + cos(φ1) × cos(φ2) × sin²(Δλ/2)
     * c = 2 × atan2(√a, √(1-a))
     * d = R × c
     *
     * @param lat1   Latitude of point 1 (degrees)
     * @param lon1   Longitude of point 1 (degrees)
     * @param lat2   Latitude of point 2 (degrees)
     * @param lon2   Longitude of point 2 (degrees)
     * @param radius Earth's radius in desired units
     * @return Distance in same units as radius
     */
    public static double calculate(double lat1, double lon1, double lat2, double lon2, double radius) {
        // Convert to radians
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        // Haversine formula
        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                        Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return radius * c;
    }

    /**
     * Calculate initial bearing (direction) from point 1 to point 2.
     *
     * @param lat1 Latitude of point 1
     * @param lon1 Longitude of point 1
     * @param lat2 Latitude of point 2
     * @param lon2 Longitude of point 2
     * @return Bearing in degrees (0-360, where 0 is North)
     */
    public static double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) -
                Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);

        double theta = Math.atan2(y, x);
        double bearing = Math.toDegrees(theta);

        // Normalize to 0-360
        return (bearing + 360) % 360;
    }

    /**
     * Calculate a destination point given a start point, bearing, and distance.
     *
     * @param lat            Starting latitude
     * @param lon            Starting longitude
     * @param bearingDegrees Bearing in degrees
     * @param distanceMeters Distance to travel in meters
     * @return Array of [latitude, longitude] of destination
     */
    public static double[] calculateDestination(double lat, double lon,
            double bearingDegrees, double distanceMeters) {
        double phi1 = Math.toRadians(lat);
        double lambda1 = Math.toRadians(lon);
        double bearing = Math.toRadians(bearingDegrees);
        double angularDistance = distanceMeters / EARTH_RADIUS_METERS;

        double phi2 = Math.asin(
                Math.sin(phi1) * Math.cos(angularDistance) +
                        Math.cos(phi1) * Math.sin(angularDistance) * Math.cos(bearing));

        double lambda2 = lambda1 + Math.atan2(
                Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(phi1),
                Math.cos(angularDistance) - Math.sin(phi1) * Math.sin(phi2));

        // Normalize longitude to -180 to 180
        lambda2 = ((lambda2 + 3 * Math.PI) % (2 * Math.PI)) - Math.PI;

        return new double[] { Math.toDegrees(phi2), Math.toDegrees(lambda2) };
    }

    /**
     * Calculate the bounding box for a point and radius.
     * Useful for database queries to narrow down candidates.
     *
     * @param lat          Center latitude
     * @param lon          Center longitude
     * @param radiusMeters Radius in meters
     * @return Array of [minLat, minLon, maxLat, maxLon]
     */
    public static double[] calculateBoundingBox(double lat, double lon, double radiusMeters) {
        // Angular radius in radians
        double angularRadius = radiusMeters / EARTH_RADIUS_METERS;

        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);

        double minLat = latRad - angularRadius;
        double maxLat = latRad + angularRadius;

        double deltaLon;
        if (minLat > Math.toRadians(-90) && maxLat < Math.toRadians(90)) {
            deltaLon = Math.asin(Math.sin(angularRadius) / Math.cos(latRad));
        } else {
            // Near poles, include all longitudes
            deltaLon = Math.PI;
        }

        double minLon = lonRad - deltaLon;
        double maxLon = lonRad + deltaLon;

        return new double[] {
                Math.toDegrees(minLat),
                Math.toDegrees(minLon),
                Math.toDegrees(maxLat),
                Math.toDegrees(maxLon)
        };
    }

    /**
     * Check if a point is within a certain distance of another point.
     * More efficient than calculating exact distance when only checking threshold.
     *
     * @param lat1         Latitude of point 1
     * @param lon1         Longitude of point 1
     * @param lat2         Latitude of point 2
     * @param lon2         Longitude of point 2
     * @param radiusMeters Maximum distance
     * @return true if points are within the radius
     */
    public static boolean isWithinRadius(double lat1, double lon1,
            double lat2, double lon2, double radiusMeters) {
        // Quick bounding box check first (much faster)
        double latDiff = Math.abs(lat1 - lat2);
        double lonDiff = Math.abs(lon1 - lon2);

        // Approximate degrees per meter at equator (rough filter)
        double approxDegreesPerMeter = 1.0 / 111_000;
        double maxDegreeDiff = radiusMeters * approxDegreesPerMeter * 1.5; // 50% buffer

        if (latDiff > maxDegreeDiff || lonDiff > maxDegreeDiff) {
            return false;
        }

        // Precise check
        return calculateMeters(lat1, lon1, lat2, lon2) <= radiusMeters;
    }

    /**
     * Calculate the midpoint between two points.
     *
     * @param lat1 Latitude of point 1
     * @param lon1 Longitude of point 1
     * @param lat2 Latitude of point 2
     * @param lon2 Longitude of point 2
     * @return Array of [latitude, longitude] of midpoint
     */
    public static double[] calculateMidpoint(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double lambda1 = Math.toRadians(lon1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double bx = Math.cos(phi2) * Math.cos(deltaLambda);
        double by = Math.cos(phi2) * Math.sin(deltaLambda);

        double phi3 = Math.atan2(
                Math.sin(phi1) + Math.sin(phi2),
                Math.sqrt((Math.cos(phi1) + bx) * (Math.cos(phi1) + bx) + by * by));

        double lambda3 = lambda1 + Math.atan2(by, Math.cos(phi1) + bx);

        return new double[] { Math.toDegrees(phi3), Math.toDegrees(lambda3) };
    }

    /**
     * Estimate ETA in seconds based on distance and average speed.
     *
     * @param lat1            Start latitude
     * @param lon1            Start longitude
     * @param lat2            End latitude
     * @param lon2            End longitude
     * @param averageSpeedKmh Average speed in km/h
     * @return Estimated time in seconds
     */
    public static int estimateEtaSeconds(double lat1, double lon1,
            double lat2, double lon2, double averageSpeedKmh) {
        double distanceKm = calculateKilometers(lat1, lon1, lat2, lon2);
        double hours = distanceKm / averageSpeedKmh;
        return (int) Math.ceil(hours * 3600);
    }

    /**
     * Estimate ETA with default urban speed (25 km/h average).
     */
    public static int estimateEtaSeconds(double lat1, double lon1, double lat2, double lon2) {
        return estimateEtaSeconds(lat1, lon1, lat2, lon2, 25.0);
    }
}
