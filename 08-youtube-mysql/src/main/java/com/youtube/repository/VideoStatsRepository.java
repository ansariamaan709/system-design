package com.youtube.repository;

import com.youtube.entity.VideoStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Video Stats Repository
 * 
 * Handles denormalized counters with optimized updates
 */
@Repository
public interface VideoStatsRepository extends JpaRepository<VideoStats, Long> {

    // Batch lookup by IDs
    @Query("SELECT vs FROM VideoStats vs WHERE vs.videoId IN :ids")
    List<VideoStats> findByVideoIds(@Param("ids") List<Long> videoIds);

    // Atomic view count increment
    @Modifying
    @Transactional
    @Query("UPDATE VideoStats vs SET vs.viewCount = vs.viewCount + :delta, " +
            "vs.updatedAt = CURRENT_TIMESTAMP WHERE vs.videoId = :videoId")
    int incrementViewCount(@Param("videoId") Long videoId, @Param("delta") long delta);

    // Atomic like count increment
    @Modifying
    @Transactional
    @Query("UPDATE VideoStats vs SET vs.likeCount = vs.likeCount + 1, " +
            "vs.updatedAt = CURRENT_TIMESTAMP WHERE vs.videoId = :videoId")
    int incrementLikeCount(@Param("videoId") Long videoId);

    // Atomic dislike count increment
    @Modifying
    @Transactional
    @Query("UPDATE VideoStats vs SET vs.dislikeCount = vs.dislikeCount + 1, " +
            "vs.updatedAt = CURRENT_TIMESTAMP WHERE vs.videoId = :videoId")
    int incrementDislikeCount(@Param("videoId") Long videoId);

    // Atomic comment count increment
    @Modifying
    @Transactional
    @Query("UPDATE VideoStats vs SET vs.commentCount = vs.commentCount + 1, " +
            "vs.updatedAt = CURRENT_TIMESTAMP WHERE vs.videoId = :videoId")
    int incrementCommentCount(@Param("videoId") Long videoId);

    // Decrement like count
    @Modifying
    @Transactional
    @Query("UPDATE VideoStats vs SET vs.likeCount = vs.likeCount - 1, " +
            "vs.updatedAt = CURRENT_TIMESTAMP WHERE vs.videoId = :videoId AND vs.likeCount > 0")
    int decrementLikeCount(@Param("videoId") Long videoId);

    // Decrement dislike count
    @Modifying
    @Transactional
    @Query("UPDATE VideoStats vs SET vs.dislikeCount = vs.dislikeCount - 1, " +
            "vs.updatedAt = CURRENT_TIMESTAMP WHERE vs.videoId = :videoId AND vs.dislikeCount > 0")
    int decrementDislikeCount(@Param("videoId") Long videoId);

    // Update engagement metrics
    @Modifying
    @Transactional
    @Query("UPDATE VideoStats vs SET vs.avgViewDurationSeconds = :avgDuration, " +
            "vs.avgPercentageViewed = :avgPercentage, " +
            "vs.updatedAt = CURRENT_TIMESTAMP WHERE vs.videoId = :videoId")
    int updateEngagementMetrics(
            @Param("videoId") Long videoId,
            @Param("avgDuration") int avgDuration,
            @Param("avgPercentage") java.math.BigDecimal avgPercentage);

    // Top videos by view count (scatter-gather)
    @Query("SELECT vs FROM VideoStats vs ORDER BY vs.viewCount DESC")
    List<VideoStats> findTopByViewCount(org.springframework.data.domain.Pageable pageable);
}
