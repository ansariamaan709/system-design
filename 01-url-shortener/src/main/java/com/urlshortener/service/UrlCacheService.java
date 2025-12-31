package com.urlshortener.service;

import com.urlshortener.entity.Url;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for caching URL mappings in Redis.
 * Provides cache-aside pattern for URL lookups.
 */
@Service
@Slf4j
public class UrlCacheService {

    private static final String URL_CACHE_PREFIX = "url:";
    private static final String CLICK_COUNT_PREFIX = "clicks:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${url-shortener.cache.ttl-hours:24}")
    private int ttlHours;

    public UrlCacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Cache a URL mapping.
     */
    public void cacheUrl(Url url) {
        try {
            String key = URL_CACHE_PREFIX + url.getShortCode();
            redisTemplate.opsForValue().set(key, url.getOriginalUrl(), ttlHours, TimeUnit.HOURS);
            log.debug("Cached URL: {} -> {}", url.getShortCode(), url.getOriginalUrl());
        } catch (Exception e) {
            log.warn("Failed to cache URL {}: {}", url.getShortCode(), e.getMessage());
        }
    }

    /**
     * Cache a URL mapping with short code and original URL.
     */
    public void cacheUrl(String shortCode, String originalUrl) {
        try {
            String key = URL_CACHE_PREFIX + shortCode;
            redisTemplate.opsForValue().set(key, originalUrl, ttlHours, TimeUnit.HOURS);
            log.debug("Cached URL: {} -> {}", shortCode, originalUrl);
        } catch (Exception e) {
            log.warn("Failed to cache URL {}: {}", shortCode, e.getMessage());
        }
    }

    /**
     * Get cached URL by short code.
     */
    public String getCachedUrl(String shortCode) {
        try {
            String key = URL_CACHE_PREFIX + shortCode;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Failed to get cached URL {}: {}", shortCode, e.getMessage());
            return null;
        }
    }

    /**
     * Evict a URL from cache.
     */
    public void evictUrl(String shortCode) {
        try {
            String key = URL_CACHE_PREFIX + shortCode;
            redisTemplate.delete(key);
            log.debug("Evicted URL from cache: {}", shortCode);
        } catch (Exception e) {
            log.warn("Failed to evict URL {}: {}", shortCode, e.getMessage());
        }
    }

    /**
     * Increment click count in cache.
     */
    public void incrementClickCount(String shortCode) {
        try {
            String key = CLICK_COUNT_PREFIX + shortCode;
            redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.warn("Failed to increment click count for {}: {}", shortCode, e.getMessage());
        }
    }

    /**
     * Get click count from cache.
     */
    public Long getClickCount(String shortCode) {
        try {
            String key = CLICK_COUNT_PREFIX + shortCode;
            String count = redisTemplate.opsForValue().get(key);
            return count != null ? Long.parseLong(count) : null;
        } catch (Exception e) {
            log.warn("Failed to get click count for {}: {}", shortCode, e.getMessage());
            return null;
        }
    }

    /**
     * Check if URL exists in cache.
     */
    public boolean existsInCache(String shortCode) {
        try {
            String key = URL_CACHE_PREFIX + shortCode;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Failed to check cache existence for {}: {}", shortCode, e.getMessage());
            return false;
        }
    }
}
