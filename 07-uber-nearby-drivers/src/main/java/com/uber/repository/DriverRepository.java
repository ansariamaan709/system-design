package com.uber.repository;

import com.uber.entity.Driver;
import com.uber.entity.DriverStatus;
import com.uber.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Driver entity operations.
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    
    List<Driver> findByStatus(DriverStatus status);
    
    List<Driver> findByCityId(String cityId);
    
    List<Driver> findByCityIdAndStatus(String cityId, DriverStatus status);
    
    List<Driver> findByCityIdAndStatusAndVehicleType(
            String cityId, 
            DriverStatus status, 
            VehicleType vehicleType);
    
    @Modifying
    @Query("UPDATE Driver d SET d.status = :status, d.updatedAt = CURRENT_TIMESTAMP WHERE d.driverId = :driverId")
    int updateStatus(@Param("driverId") UUID driverId, @Param("status") DriverStatus status);
    
    @Modifying
    @Query("UPDATE Driver d SET d.totalTrips = d.totalTrips + 1 WHERE d.driverId = :driverId")
    int incrementTripCount(@Param("driverId") UUID driverId);
    
    @Query("SELECT COUNT(d) FROM Driver d WHERE d.cityId = :cityId AND d.status = :status")
    long countByCityIdAndStatus(@Param("cityId") String cityId, @Param("status") DriverStatus status);
}
