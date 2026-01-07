package com.youtube.service;

import com.youtube.dto.ViewEvent;
import com.youtube.entity.WatchHistory;
import com.youtube.repository.VideoStatsRepository;
import com.youtube.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * View Count Service - High-throughput view counting
 * 
 * Multi-tier aggregation strategy:
 * 1. Kafka buffering (handles burst traffic)
 * 2. Redis counter (real-time display, ~1s delay)
 * 3. MySQL (periodic flush, source of truth)
 * 
 * At YouTube scale (1B hours/day = ~4M views/second):
 * - Cannot write to MySQL on every view
 * - Buffer in Redis, flush periodically
 * - Accept eventual consistency for view counts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ViewCountService {

    private final StringRedisTemplate redisTemplate;
    private final VideoStatsRepository videoStatsRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String VIEW_COUNT_KEY = "view_count:";
    private static final String DIRTY_VIDEOS_KEY = "dirty_view_counts";

    /**
     * Record a view event (async via Kafka)
     */
    public void recordView(ViewEvent event) {
        // Publish to Kafka for async processing
        kafkaTemplate.send("view-events", String.valueOf(event.getVideoId()), event);
    }

    /**
     * Process view event from Kafka
     */
    @KafkaListener(topics = "view-events", groupId = "view-count-processor")
    public void processViewEvent(ViewEvent event) {
        try {
            // Increment Redis counter (real-time)
            String key = VIEW_COUNT_KEY + event.getVideoId();
            redisTemplate.opsForValue().increment(key);

            // Set TTL to prevent stale counters from accumulating
            redisTemplate.expire(key, 1, TimeUnit.HOURS);

            // Mark as dirty for MySQL flush
            redisTemplate.opsForSet().add(DIRTY_VIDEOS_KEY, String.valueOf(event.getVideoId()));

            // Update watch history if user is logged in
            if (event.getUserId() != null) {
                updateWatchHistory(event);
            }

        } catch (Exception e) {
            log.error("Error processing view event for video {}: {}",
                    event.getVideoId(), e.getMessage());
        }
    }

    /**
     * Get real-time view count from Redis
     */
    public long getViewCount(Long videoId) {
        String key = VIEW_COUNT_KEY + videoId;
        String count = redisTemplate.opsForValue().get(key);

        if (count != null) {
            return Long.parseLong(count);
        }

        // Fallback to MySQL
        return videoStatsRepository.findById(videoId)
                .map(stats -> stats.getViewCount())
                .orElse(0L);
    }

    /**
     * Flush view counts to MySQL (scheduled job)
     * Runs every 5 minutes
     */
    @Scheduled(fixedRateString = "${youtube.view-count.flush-interval:300000}")
    @Transactional
    public void flushViewCountsToMySQL() {
        Set<String> dirtyVideoIds = redisTemplate.opsForSet().members(DIRTY_VIDEOS_KEY);

        if (dirtyVideoIds == null || dirtyVideoIds.isEmpty()) {
            return;
        }

        log.info("Flushing view counts for {} videos to MySQL", dirtyVideoIds.size());

        int flushed = 0;
        for (String videoIdStr : dirtyVideoIds) {
            try {
                Long videoId = Long.parseLong(videoIdStr);
                String key = VIEW_COUNT_KEY + videoId;
                String countStr = redisTemplate.opsForValue().get(key);

                if (countStr != null) {
                    long count = Long.parseLong(countStr);

                    // Get current MySQL count
                    long mysqlCount = videoStatsRepository.findById(videoId)
                            .map(stats -> stats.getViewCount())
                            .orElse(0L);

                    // Only update if Redis count is higher
                    if (count > mysqlCount) {
                        long delta = count - mysqlCount;
                        videoStatsRepository.incrementViewCount(videoId, delta);
                        flushed++;
                    }
                }

                // Remove from dirty set
                redisTemplate.opsForSet().remove(DIRTY_VIDEOS_KEY, videoIdStr);

            } catch (Exception e) {
                log.error("Error flushing view count for video {}: {}", videoIdStr, e.getMessage());
            }
        }

        log.info("Flushed {} view counts to MySQL", flushed);
    }

    /**
     * Initialize Redis counter from MySQL (cache warming)
     */
    public void warmCache(Long videoId) {
        videoStatsRepository.findById(videoId).ifPresent(stats -> {
            String key = VIEW_COUNT_KEY + videoId;
            redisTemplate.opsForValue().set(key, String.valueOf(stats.getViewCount()));
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        });
    }

    /**
     * Update user's watch history
     */
    private void updateWatchHistory(ViewEvent event) {
        try {
            WatchHistory history = WatchHistory.builder()
                    .userId(event.getUserId())
                    .videoId(event.getVideoId())
                    .watchedAt(LocalDateTime.now())
                    .watchDurationSeconds(event.getWatchDurationSeconds() != null ? event.getWatchDurationSeconds() : 0)
                    .percentageWatched(
                            event.getPercentageWatched() != null ? BigDecimal.valueOf(event.getPercentageWatched())
                                    : BigDecimal.ZERO)
                    .build();

            watchHistoryRepository.save(history);
        } catch (Exception e) {
            // Watch history is not critical - log and continue
            log.warn("Failed to update watch history for user {} video {}: {}",
                    event.getUserId(), event.getVideoId(), e.getMessage());
        }
    }

    /**
     * Format view count for display
     */
    public String formatViewCount(long viewCount) {
        if (viewCount < 1000) {
            return String.valueOf(viewCount);
        } else if (viewCount < 1_000_000) {
            return String.format("%.1fK", viewCount / 1000.0);
        } else if (viewCount < 1_000_000_000) {
            return String.format("%.1fM", viewCount / 1_000_000.0);
        } else {
            return String.format("%.1fB", viewCount / 1_000_000_000.0);
        }
    }
}
