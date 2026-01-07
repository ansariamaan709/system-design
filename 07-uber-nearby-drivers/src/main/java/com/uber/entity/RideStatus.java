package com.uber.entity;

/**
 * Ride request status.
 */
public enum RideStatus {
    PENDING, // Waiting for driver match
    MATCHING, // Actively searching for driver
    DRIVER_ASSIGNED, // Driver found and assigned
    DRIVER_EN_ROUTE, // Driver heading to pickup
    ARRIVED, // Driver at pickup location
    IN_PROGRESS, // Ride in progress
    COMPLETED, // Ride completed successfully
    CANCELLED, // Ride cancelled
    NO_DRIVERS // No drivers available
}
