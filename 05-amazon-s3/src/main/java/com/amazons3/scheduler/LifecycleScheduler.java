package com.amazons3.scheduler;

import com.amazons3.entity.LifecycleRule;
import com.amazons3.entity.S3Object;
import com.amazons3.repository.LifecycleRuleRepository;
import com.amazons3.repository.ObjectRepository;
import com.amazons3.service.MultipartUploadService;
import com.amazons3.storage.StorageEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled tasks for lifecycle management, cleanup, and maintenance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LifecycleScheduler {

    private final ObjectRepository objectRepository;
    private final LifecycleRuleRepository lifecycleRuleRepository;
    private final MultipartUploadService multipartUploadService;
    private final StorageEngine storageEngine;

    /**
     * Process lifecycle rules - runs hourly
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void processLifecycleRules() {
        log.info("[LIFECYCLE] Starting lifecycle rule processing");

        List<LifecycleRule> rules = lifecycleRuleRepository.findAll().stream()
                .filter(r -> r.getStatus() == LifecycleRule.RuleStatus.ENABLED)
                .toList();

        int expiredObjects = 0;
        int transitionedObjects = 0;

        for (LifecycleRule rule : rules) {
            try {
                // Process expirations
                if (rule.getExpirationDays() != null) {
                    Instant threshold = Instant.now().minus(rule.getExpirationDays(), ChronoUnit.DAYS);
                    // Find and delete expired objects
                    // Note: This is simplified - production would use batch processing
                }

                // Process transitions
                if (rule.getTransitionDays() != null && rule.getTransitionStorageClass() != null) {
                    Instant threshold = Instant.now().minus(rule.getTransitionDays(), ChronoUnit.DAYS);
                    // Find and transition objects
                }
            } catch (Exception e) {
                log.error("[LIFECYCLE] Error processing rule {}: {}", rule.getRuleId(), e.getMessage());
            }
        }

        log.info("[LIFECYCLE] Completed: {} expired, {} transitioned", expiredObjects, transitionedObjects);
    }

    /**
     * Clean up expired objects - runs every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    @Transactional
    public void cleanupExpiredObjects() {
        log.debug("[CLEANUP] Starting expired object cleanup");

        List<S3Object> expiredObjects = objectRepository.findExpiredObjects(Instant.now());
        int deleted = 0;

        for (S3Object object : expiredObjects) {
            try {
                objectRepository.delete(object);
                deleted++;
            } catch (Exception e) {
                log.error("[CLEANUP] Failed to delete expired object {}: {}",
                        object.getObjectId(), e.getMessage());
            }
        }

        if (deleted > 0) {
            log.info("[CLEANUP] Deleted {} expired objects", deleted);
        }
    }

    /**
     * Clean up abandoned multipart uploads - runs daily
     */
    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    public void cleanupAbandonedUploads() {
        log.info("[CLEANUP] Starting multipart upload cleanup");
        int cleaned = multipartUploadService.cleanupExpiredUploads();
        log.info("[CLEANUP] Cleaned {} abandoned uploads", cleaned);
    }

    /**
     * Clean up temp files - runs every 30 minutes
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void cleanupTempFiles() {
        try {
            // Delete temp files older than 1 hour
            int deleted = storageEngine.cleanupTempFiles(3600000);
            if (deleted > 0) {
                log.info("[CLEANUP] Deleted {} temp files", deleted);
            }
        } catch (Exception e) {
            log.error("[CLEANUP] Failed to clean temp files: {}", e.getMessage());
        }
    }

    /**
     * Storage statistics - runs hourly
     */
    @Scheduled(cron = "0 30 * * * *")
    public void logStorageStats() {
        try {
            StorageEngine.StorageStats stats = storageEngine.getStats();
            log.info("[STATS] Storage: {} files, {} bytes total",
                    stats.fileCount(), stats.totalSizeBytes());
        } catch (Exception e) {
            log.error("[STATS] Failed to get storage stats: {}", e.getMessage());
        }
    }
}
