package com.uber.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache configuration for L1 caching.
 * 
 * Caches:
 * - driverMetadata: Driver profile data (60s TTL)
 * - cityConfig: City configurations (300s TTL)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${cache.driver-metadata.ttl-seconds:60}")
    private int driverMetadataTtl;

    @Value("${cache.driver-metadata.max-size:100000}")
    private int driverMetadataMaxSize;

    @Value("${cache.city-config.ttl-seconds:300}")
    private int cityConfigTtl;

    @Value("${cache.city-config.max-size:1000}")
    private int cityConfigMaxSize;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(100000)
                .recordStats());
        return cacheManager;
    }

    @Bean
    public Caffeine<Object, Object> driverMetadataCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(driverMetadataTtl, TimeUnit.SECONDS)
                .maximumSize(driverMetadataMaxSize)
                .recordStats();
    }

    @Bean
    public Caffeine<Object, Object> cityConfigCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(cityConfigTtl, TimeUnit.SECONDS)
                .maximumSize(cityConfigMaxSize)
                .recordStats();
    }
}
