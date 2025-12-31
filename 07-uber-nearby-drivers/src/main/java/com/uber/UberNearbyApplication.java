package com.uber;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Uber Nearby Drivers Application
 * 
 * A real-time geospatial system for finding nearby drivers at scale.
 * Handles millions of location updates and ride requests per second.
 * 
 * Key Features:
 * - Real-time driver location tracking
 * - Geospatial queries using H3/Geohash
 * - Driver-rider matching
 * - WebSocket-based live updates
 * 
 * @author System Design Series
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class UberNearbyApplication {

    public static void main(String[] args) {
        SpringApplication.run(UberNearbyApplication.class, args);
    }
}
