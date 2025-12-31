package com.urlshortener.scheduler;

import com.urlshortener.config.RateLimitFilter;
import com.urlshortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@EnableScheduling
public class ScheduledTasks {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);

    private final UrlRepository urlRepository;
    private final RateLimitFilter rateLimitFilter;

    public ScheduledTasks(UrlRepository urlRepository, RateLimitFilter rateLimitFilter) {
        this.urlRepository = urlRepository;
        this.rateLimitFilter = rateLimitFilter;
    }

    /**
     * Deactivate expired URLs every hour
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void deactivateExpiredUrls() {
        logger.info("Starting scheduled task: deactivating expired URLs");
        try {
            int deactivated = urlRepository.deactivateExpiredUrls(LocalDateTime.now());
            logger.info("Deactivated {} expired URLs", deactivated);
        } catch (Exception e) {
            logger.error("Error deactivating expired URLs", e);
        }
    }

    /**
     * Cleanup rate limit buckets every 30 minutes
     */
    @Scheduled(fixedRate = 1800000) // Every 30 minutes
    public void cleanupRateLimitBuckets() {
        logger.info("Starting scheduled task: cleaning up rate limit buckets");
        try {
            rateLimitFilter.cleanupBuckets();
            logger.info("Rate limit bucket cleanup completed");
        } catch (Exception e) {
            logger.error("Error cleaning up rate limit buckets", e);
        }
    }

    /**
     * Log application stats every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void logApplicationStats() {
        try {
            long totalUrls = urlRepository.count();
            logger.info("Application stats - Total URLs in database: {}", totalUrls);
        } catch (Exception e) {
            logger.error("Error logging application stats", e);
        }
    }
}
