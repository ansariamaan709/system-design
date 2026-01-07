package com.uber.repository;

import com.uber.entity.DriverLocation;
import com.uber.entity.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for DriverLocation entity operations.
 * Note: Most real-time queries use Redis, not this repository.
 * This is primarily for PostgreSQL backup and analytics.
 */
@Repository
public interface DriverLocationRepository extends JpaRepository<DriverLocation, UUID> {

    List<DriverLocation> findByStatus(DriverStatus status);

    List<DriverLocation> findByH3Index(Long h3Index);

    List<DriverLocation> findByH3IndexIn(List<Long> h3Indexes);

    @Query("SELECT dl FROM DriverLocation dl WHERE dl.geohash LIKE :prefix%")
    List<DriverLocation> findByGeohashPrefix(@Param("prefix") String prefix);

    @Query("SELECT dl FROM DriverLocation dl WHERE dl.status = 'AVAILABLE' AND dl.h3Index IN :h3Indexes")
    List<DriverLocation> findAvailableInH3Cells(@Param("h3Indexes") List<Long> h3Indexes);

    @Modifying
    @Query("DELETE FROM DriverLocation dl WHERE dl.updatedAt < :threshold")
    int deleteStaleLocations(@Param("threshold") Instant threshold);

    @Modifying
    @Query("UPDATE DriverLocation dl SET dl.status = :status WHERE dl.driverId = :driverId")
    int updateStatus(@Param("driverId") UUID driverId, @Param("status") DriverStatus status);

    // Native query using PostGIS for spatial operations
    @Query(value = """
            SELECT dl.* FROM driver_locations dl
            WHERE dl.status = 'AVAILABLE'
            AND ST_DWithin(
                dl.location,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :radiusMeters
            )
            ORDER BY ST_Distance(
                dl.location,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
            )
            LIMIT :limit
            """, nativeQuery = true)
    List<DriverLocation> findNearbyAvailable(
            @Param("lat") double latitude,
            @Param("lng") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("limit") int limit);
}
