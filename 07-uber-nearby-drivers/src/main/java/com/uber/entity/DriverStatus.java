package com.uber.entity;

/**
 * Driver availability status.
 */
public enum DriverStatus {
    AVAILABLE, // Ready to accept rides
    BUSY, // Currently on a trip
    OFFLINE // Not accepting rides
}
