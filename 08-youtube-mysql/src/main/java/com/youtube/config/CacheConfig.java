package com.youtube.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Cache Configuration - Multi-level caching strategy
 * 
 * L1: Caffeine (in-process) - ~10K items, 30-40% hit rate
 * L2: Redis (distributed) - 1TB+, 60-70% hit rate
 * L3: MySQL (via Vitess) - source of truth
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${youtube.cache.video-metadata-ttl:300}")
    private int videoMetadataTtl;

    @Value("${youtube.cache.video-stats-ttl:30}")
    private int videoStatsTtl;

    @Value("${youtube.cache.channel-info-ttl:600}")
    private int channelInfoTtl;

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(defaultCaffeine());
        return cacheManager;
    }

    /**
     * Video metadata cache - longer TTL, moderate size
     */
    @Bean
    public Caffeine<Object, Object> videoCaffeine() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(videoMetadataTtl, TimeUnit.SECONDS)
                .recordStats();
    }

    /**
     * Video stats cache - short TTL (frequently updated)
     */
    @Bean
    public Caffeine<Object, Object> videoStatsCaffeine() {
        return Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(videoStatsTtl, TimeUnit.SECONDS)
                .recordStats();
    }

    /**
     * Channel info cache - longer TTL
     */
    @Bean
    public Caffeine<Object, Object> channelCaffeine() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(channelInfoTtl, TimeUnit.SECONDS)
                .recordStats();
    }

    /**
     * Default cache configuration
     */
    private Caffeine<Object, Object> defaultCaffeine() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats();
    }
}
