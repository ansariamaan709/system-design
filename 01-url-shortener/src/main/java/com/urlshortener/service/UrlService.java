package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final Base62Encoder base62Encoder;
    private final UrlCacheService cacheService;
    private final Counter urlsCreatedCounter;
    private final Counter urlsResolvedCounter;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public UrlService(UrlRepository urlRepository,
            ClickEventRepository clickEventRepository,
            SnowflakeIdGenerator idGenerator,
            Base62Encoder base62Encoder,
            UrlCacheService cacheService,
            MeterRegistry meterRegistry) {
        this.urlRepository = urlRepository;
        this.clickEventRepository = clickEventRepository;
        this.idGenerator = idGenerator;
        this.base62Encoder = base62Encoder;
        this.cacheService = cacheService;

        this.urlsCreatedCounter = Counter.builder("urls.created")
                .description("Total URLs created")
                .register(meterRegistry);
        this.urlsResolvedCounter = Counter.builder("urls.resolved")
                .description("Total URL redirects")
                .register(meterRegistry);
    }

    @Transactional
    public UrlResponse createShortUrl(CreateUrlRequest request) {
        log.info("Creating short URL for: {}", request.getOriginalUrl());

        // Normalize URL (add https:// if missing)
        String normalizedUrl = normalizeUrl(request.getOriginalUrl());
        request.setOriginalUrl(normalizedUrl);

        // Check for existing URL (deduplication)
        Optional<Url> existing = findExistingUrl(normalizedUrl, request.getUserId());
        if (existing.isPresent() && request.getCustomAlias() == null) {
            log.info("Returning existing short URL: {}", existing.get().getShortCode());
            return mapToResponse(existing.get());
        }

        // Generate unique ID using Snowflake
        long id = idGenerator.nextId();

        String shortCode;
        boolean isCustomAlias = false;

        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            // Use custom alias
            shortCode = request.getCustomAlias();
            if (urlRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyExistsException("Custom alias '" + shortCode + "' already exists");
            }
            isCustomAlias = true;
        } else {
            // Generate short code using Snowflake ID
            shortCode = base62Encoder.encode(id);
        }

        Url url = Url.builder()
                .id(id)
                .shortCode(shortCode)
                .originalUrl(request.getOriginalUrl())
                .userId(request.getUserId())
                .expiresAt(request.getExpiresAt())
                .isCustomAlias(isCustomAlias)
                .isActive(true)
                .clickCount(0L)
                .build();

        Url savedUrl = urlRepository.save(url);

        // Cache the URL
        cacheService.cacheUrl(savedUrl);

        urlsCreatedCounter.increment();
        log.info("Created short URL: {} -> {}", shortCode, request.getOriginalUrl());

        return mapToResponse(savedUrl);
    }

    @Transactional(readOnly = true)
    public String resolveUrl(String shortCode) {
        log.info("[REQUEST] Resolving short code: {}", shortCode);

        // Try cache first
        String cachedUrl = cacheService.getCachedUrl(shortCode);
        if (cachedUrl != null) {
            log.info("[CACHE HIT] Found URL in Redis cache for: {} -> {}", shortCode, cachedUrl);
            urlsResolvedCounter.increment();
            return cachedUrl;
        }

        // Cache miss - query database
        log.info("[CACHE MISS] URL not in cache, querying PostgreSQL for: {}", shortCode);
        Url url = urlRepository.findActiveByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found for code: " + shortCode));

        if (url.isExpired()) {
            throw new UrlExpiredException("URL has expired");
        }

        log.info("[DATABASE HIT] Found in PostgreSQL: {} -> {}", shortCode, url.getOriginalUrl());

        // Update cache for future requests
        cacheService.cacheUrl(url);
        log.info("[CACHE UPDATE] Stored in Redis cache: {}", shortCode);

        urlsResolvedCounter.increment();
        return url.getOriginalUrl();
    }

    @Transactional
    public void incrementClickCount(String shortCode) {
        urlRepository.incrementClickCount(shortCode);
        cacheService.incrementClickCount(shortCode);
    }

    @Transactional(readOnly = true)
    public UrlResponse getUrlInfo(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found for code: " + shortCode));
        return mapToResponse(url);
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse getUrlStats(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found for code: " + shortCode));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24Hours = now.minusHours(24);
        LocalDateTime last7Days = now.minusDays(7);
        LocalDateTime last30Days = now.minusDays(30);

        // Get click counts
        long clicksLast24Hours = clickEventRepository.countByShortCodeSince(shortCode, last24Hours);
        long clicksLast7Days = clickEventRepository.countByShortCodeSince(shortCode, last7Days);
        long clicksLast30Days = clickEventRepository.countByShortCodeSince(shortCode, last30Days);

        // Get aggregated stats
        Map<String, Long> countryStats = aggregateStats(clickEventRepository.getCountryStats(shortCode));
        Map<String, Long> browserStats = aggregateStats(clickEventRepository.getBrowserStats(shortCode));
        Map<String, Long> deviceStats = aggregateStats(clickEventRepository.getDeviceStats(shortCode));

        // Get daily clicks for last 30 days
        List<UrlStatsResponse.DailyClickStat> dailyClicks = clickEventRepository
                .getDailyClickStats(shortCode, last30Days)
                .stream()
                .map(row -> UrlStatsResponse.DailyClickStat.builder()
                        .date(row[0] instanceof LocalDate ? (LocalDate) row[0] : LocalDate.parse(row[0].toString()))
                        .clicks((Long) row[1])
                        .build())
                .collect(Collectors.toList());

        return UrlStatsResponse.builder()
                .shortCode(shortCode)
                .originalUrl(url.getOriginalUrl())
                .totalClicks(url.getClickCount())
                .clicksLast24Hours(clicksLast24Hours)
                .clicksLast7Days(clicksLast7Days)
                .clicksLast30Days(clicksLast30Days)
                .clicksByCountry(countryStats)
                .clicksByBrowser(browserStats)
                .clicksByDevice(deviceStats)
                .dailyClicks(dailyClicks)
                .build();
    }

    @Transactional
    @CacheEvict(value = "urls", key = "#shortCode")
    public void deleteUrl(String shortCode, String userId) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found for code: " + shortCode));

        if (userId != null && !userId.equals(url.getUserId())) {
            throw new SecurityException("Not authorized to delete this URL");
        }

        url.setIsActive(false);
        urlRepository.save(url);
        cacheService.evictUrl(shortCode);

        log.info("Deleted URL: {}", shortCode);
    }

    @Transactional(readOnly = true)
    public List<UrlResponse> getUserUrls(String userId) {
        return urlRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        // Add https:// if no protocol is specified
        if (!url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return "https://" + url;
        }
        return url;
    }

    private Optional<Url> findExistingUrl(String originalUrl, String userId) {
        if (userId != null) {
            return urlRepository.findByOriginalUrlAndUserId(originalUrl, userId);
        }
        return urlRepository.findByOriginalUrlAndUserIdIsNull(originalUrl);
    }

    private UrlResponse mapToResponse(Url url) {
        return UrlResponse.builder()
                .id(url.getId())
                .shortCode(url.getShortCode())
                .shortUrl(baseUrl + "/" + url.getShortCode())
                .originalUrl(url.getOriginalUrl())
                .clickCount(url.getClickCount())
                .isActive(url.getIsActive())
                .expiresAt(url.getExpiresAt())
                .createdAt(url.getCreatedAt())
                .isCustomAlias(url.getIsCustomAlias())
                .build();
    }

    private Map<String, Long> aggregateStats(List<Object[]> results) {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (Object[] row : results) {
            String key = row[0] != null ? row[0].toString() : "Unknown";
            Long count = (Long) row[1];
            stats.put(key, count);
        }
        return stats;
    }
}
