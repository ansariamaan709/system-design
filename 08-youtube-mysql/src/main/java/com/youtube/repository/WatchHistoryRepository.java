package com.youtube.repository;

import com.youtube.entity.WatchHistory;
import com.youtube.entity.WatchHistoryId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Watch History Repository
 * 
 * In Vitess: Sharded by user_id
 */
@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, WatchHistoryId> {

    // User's watch history (routes to user's shard)
    Page<WatchHistory> findByUserIdOrderByWatchedAtDesc(Long userId, Pageable pageable);

    // Recent watch history
    @Query("SELECT wh FROM WatchHistory wh WHERE wh.userId = :userId " +
            "AND wh.watchedAt >= :since ORDER BY wh.watchedAt DESC")
    List<WatchHistory> findRecentByUser(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    // Delete user's watch history
    @Modifying
    @Transactional
    void deleteByUserId(Long userId);

    // Delete specific video from history
    @Modifying
    @Transactional
    void deleteByUserIdAndVideoId(Long userId, Long videoId);

    // Get video IDs from user's history
    @Query("SELECT wh.videoId FROM WatchHistory wh WHERE wh.userId = :userId " +
            "ORDER BY wh.watchedAt DESC")
    List<Long> findVideoIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    // Update watch progress
    @Modifying
    @Transactional
    @Query("UPDATE WatchHistory wh SET wh.watchDurationSeconds = :duration, " +
            "wh.percentageWatched = :percentage, wh.watchedAt = CURRENT_TIMESTAMP " +
            "WHERE wh.userId = :userId AND wh.videoId = :videoId")
    int updateWatchProgress(
            @Param("userId") Long userId,
            @Param("videoId") Long videoId,
            @Param("duration") int duration,
            @Param("percentage") java.math.BigDecimal percentage);
}
