package com.kafka.service;

import com.kafka.storage.Log;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Log Manager - Manages all partition logs on a broker.
 * 
 * Responsibilities:
 * 1. Create and manage Log instances for each partition
 * 2. Handle log directory initialization and recovery
 * 3. Schedule background tasks (retention, compaction)
 * 4. Coordinate shutdown and cleanup
 */
@Slf4j
@Service
public class LogManager {

    @Value("${kafka.log.dir:/tmp/kafka-logs}")
    private String logDir;

    @Value("${kafka.log.retention.check.interval.ms:300000}")
    private long retentionCheckIntervalMs;

    @Value("${kafka.log.cleaner.enable:true}")
    private boolean cleanerEnabled;

    @Value("${kafka.log.segment.bytes:1073741824}")
    private long defaultSegmentBytes;

    @Value("${kafka.log.retention.ms:604800000}")
    private long defaultRetentionMs;

    @Value("${kafka.log.retention.bytes:-1}")
    private long defaultRetentionBytes;

    // Map of topic-partition to Log
    private final ConcurrentMap<TopicPartitionKey, Log> logs = new ConcurrentHashMap<>();

    // Background task executor
    private ScheduledExecutorService scheduler;

    // Shutdown flag
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Getter
    private volatile boolean initialized = false;

    @PostConstruct
    public void initialize() throws IOException {
        log.info("Initializing LogManager with log directory: {}", logDir);

        // Create log directory if it doesn't exist
        Path logPath = Paths.get(logDir);
        if (!Files.exists(logPath)) {
            Files.createDirectories(logPath);
            log.info("Created log directory: {}", logDir);
        }

        // Recover existing logs
        recoverLogs();

        // Start background tasks
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "log-manager-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Schedule retention enforcement
        scheduler.scheduleWithFixedDelay(
                this::enforceRetention,
                retentionCheckIntervalMs,
                retentionCheckIntervalMs,
                TimeUnit.MILLISECONDS);

        // Schedule log cleanup
        if (cleanerEnabled) {
            scheduler.scheduleWithFixedDelay(
                    this::cleanLogs,
                    retentionCheckIntervalMs * 2,
                    retentionCheckIntervalMs,
                    TimeUnit.MILLISECONDS);
        }

        initialized = true;
        log.info("LogManager initialized with {} existing logs", logs.size());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down LogManager");
        shuttingDown.set(true);

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Close all logs
        logs.values().forEach(l -> {
            try {
                l.close();
            } catch (Exception e) {
                log.error("Error closing log", e);
            }
        });

        log.info("LogManager shutdown complete");
    }

    /**
     * Get or create a log for a topic-partition.
     */
    public Log getOrCreateLog(String topic, int partition) throws IOException {
        TopicPartitionKey key = new TopicPartitionKey(topic, partition);

        return logs.computeIfAbsent(key, k -> {
            try {
                return createLog(topic, partition);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create log for " + topic + "-" + partition, e);
            }
        });
    }

    /**
     * Get an existing log.
     */
    public Log getLog(String topic, int partition) throws IOException {
        TopicPartitionKey key = new TopicPartitionKey(topic, partition);
        Log existingLog = logs.get(key);

        if (existingLog == null) {
            return getOrCreateLog(topic, partition);
        }

        return existingLog;
    }

    /**
     * Delete a log for a topic-partition.
     */
    public void deleteLog(String topic, int partition) throws IOException {
        TopicPartitionKey key = new TopicPartitionKey(topic, partition);
        Log removedLog = logs.remove(key);

        if (removedLog != null) {
            removedLog.close();

            // Delete log directory
            Path logPath = getLogPath(topic, partition);
            deleteDirectory(logPath);

            log.info("Deleted log for {}-{}", topic, partition);
        }
    }

    /**
     * Delete all logs for a topic.
     */
    public void deleteTopicLogs(String topic) throws IOException {
        List<TopicPartitionKey> toDelete = logs.keySet().stream()
                .filter(k -> k.topic().equals(topic))
                .toList();

        for (TopicPartitionKey key : toDelete) {
            deleteLog(key.topic(), key.partition());
        }
    }

    /**
     * Get all managed logs.
     */
    public Collection<Log> getAllLogs() {
        return Collections.unmodifiableCollection(logs.values());
    }

    /**
     * Get log size statistics.
     */
    public LogStats getStats() {
        long totalSize = 0;
        long totalMessages = 0;
        int segmentCount = 0;

        for (Log alog : logs.values()) {
            totalSize += alog.size();
            totalMessages += alog.getLogEndOffset().get() - alog.getLogStartOffset().get();
            segmentCount += alog.getSegments().size();
        }

        return new LogStats(logs.size(), segmentCount, totalSize, totalMessages);
    }

    /**
     * Create a new log instance.
     */
    private Log createLog(String topic, int partition) throws IOException {
        Path logPath = getLogPath(topic, partition);
        Files.createDirectories(logPath);

        String topicPartition = topic + "-" + partition;
        Log.LogConfig config = new Log.LogConfig(
                (int) defaultSegmentBytes,
                defaultRetentionMs,
                4096,
                10485760,
                defaultRetentionMs,
                defaultRetentionBytes,
                false
        );
        Log newLog = new Log(topicPartition, logPath, config);
        log.debug("Created log for {}-{} at {}", topic, partition, logPath);

        return newLog;
    }

    /**
     * Recover existing logs from disk.
     */
    private void recoverLogs() throws IOException {
        File logDirectory = new File(logDir);
        File[] topicDirs = logDirectory.listFiles(File::isDirectory);

        if (topicDirs == null) {
            return;
        }

        for (File topicDir : topicDirs) {
            String dirName = topicDir.getName();

            // Parse topic-partition directory name (format: topic-partition)
            int lastDash = dirName.lastIndexOf('-');
            if (lastDash <= 0) {
                log.warn("Skipping invalid log directory: {}", dirName);
                continue;
            }

            try {
                String topic = dirName.substring(0, lastDash);
                int partition = Integer.parseInt(dirName.substring(lastDash + 1));

                String topicPartition = topic + "-" + partition;
                Log.LogConfig config = new Log.LogConfig(
                        (int) defaultSegmentBytes,
                        defaultRetentionMs,
                        4096,
                        10485760,
                        defaultRetentionMs,
                        defaultRetentionBytes,
                        false
                );
                Log recoveredLog = new Log(
                        topicPartition,
                        topicDir.toPath(),
                        config);

                logs.put(new TopicPartitionKey(topic, partition), recoveredLog);
                log.info("Recovered log for {}-{}: offsets [{}, {}]",
                        topic, partition,
                        recoveredLog.getLogStartOffset(),
                        recoveredLog.getLogEndOffset());

            } catch (NumberFormatException e) {
                log.warn("Skipping directory with invalid partition number: {}", dirName);
            } catch (IOException e) {
                log.error("Failed to recover log from {}", topicDir, e);
            }
        }
    }

    /**
     * Enforce retention policy on all logs.
     */
    private void enforceRetention() {
        if (shuttingDown.get())
            return;

        log.debug("Running retention enforcement");

        for (Map.Entry<TopicPartitionKey, Log> entry : logs.entrySet()) {
            try {
                int deleted = entry.getValue().deleteOldSegments();
                if (deleted > 0) {
                    log.info("Deleted {} segments from {}-{} due to retention",
                            deleted, entry.getKey().topic(), entry.getKey().partition());
                }
            } catch (Exception e) {
                log.error("Error enforcing retention for {}", entry.getKey(), e);
            }
        }
    }

    /**
     * Clean logs (compaction for compacted topics).
     */
    private void cleanLogs() {
        if (shuttingDown.get())
            return;

        log.debug("Running log cleaner");
        // Log compaction logic would go here
    }

    /**
     * Get the file path for a topic-partition log.
     */
    private Path getLogPath(String topic, int partition) {
        return Paths.get(logDir, topic + "-" + partition);
    }

    /**
     * Recursively delete a directory.
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (var stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    /**
     * Truncate log to a specific offset (for replica synchronization).
     */
    public void truncateTo(String topic, int partition, long offset) throws IOException {
        Log partitionLog = getLog(topic, partition);
        if (partitionLog != null) {
            partitionLog.truncateTo(offset);
            log.info("Truncated {}-{} to offset {}", topic, partition, offset);
        }
    }

    /**
     * Flush all logs to disk.
     */
    public void flushAll() {
        logs.values().forEach(l -> {
            try {
                l.flush();
            } catch (IOException e) {
                log.error("Error flushing log", e);
            }
        });
    }

    // DTOs
    private record TopicPartitionKey(String topic, int partition) {
    }

    public record LogStats(
            int logCount,
            int segmentCount,
            long totalSizeBytes,
            long totalMessages) {
    }
}
