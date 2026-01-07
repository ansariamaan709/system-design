package com.youtube.repository;

import com.youtube.entity.Video;
import com.youtube.entity.UploadStatus;
import com.youtube.entity.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Video Repository
 * 
 * Query patterns optimized for Vitess sharding:
 * - Point lookups by video_id (optimal)
 * - Channel queries use secondary lookup vindex
 */
@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    // Point lookup - routes to single shard
    // Already provided by JpaRepository.findById()

    // Channel videos - uses lookup vindex in Vitess
    Page<Video> findByChannelIdAndUploadStatusOrderByPublishedAtDesc(
            Long channelId,
            UploadStatus status,
            Pageable pageable);

    // Count videos by channel
    long countByChannelIdAndUploadStatus(Long channelId, UploadStatus status);

    // Public videos by channel
    @Query("SELECT v FROM Video v WHERE v.channelId = :channelId " +
            "AND v.uploadStatus = 'PUBLISHED' " +
            "AND v.visibility = 'PUBLIC' " +
            "ORDER BY v.publishedAt DESC")
    Page<Video> findPublicVideosByChannel(@Param("channelId") Long channelId, Pageable pageable);

    // Videos by category (scatter-gather in Vitess)
    @Query("SELECT v FROM Video v WHERE v.categoryId = :categoryId " +
            "AND v.uploadStatus = 'PUBLISHED' " +
            "AND v.visibility = 'PUBLIC' " +
            "ORDER BY v.publishedAt DESC")
    Page<Video> findByCategory(@Param("categoryId") Integer categoryId, Pageable pageable);

    // Recent videos (scatter-gather - use with caution)
    @Query("SELECT v FROM Video v WHERE v.uploadStatus = 'PUBLISHED' " +
            "AND v.visibility = 'PUBLIC' " +
            "AND v.publishedAt >= :since " +
            "ORDER BY v.publishedAt DESC")
    List<Video> findRecentVideos(@Param("since") LocalDateTime since, Pageable pageable);

    // Batch lookup by IDs (routes to specific shards)
    @Query("SELECT v FROM Video v WHERE v.videoId IN :ids")
    List<Video> findByVideoIds(@Param("ids") List<Long> videoIds);

    // Shorts by channel
    Page<Video> findByChannelIdAndIsShortTrueAndUploadStatusOrderByPublishedAtDesc(
            Long channelId,
            UploadStatus status,
            Pageable pageable);

    // Live streams by channel
    Page<Video> findByChannelIdAndIsLiveStreamTrueOrderByPublishedAtDesc(
            Long channelId,
            Pageable pageable);

    // Search within channel (for Creator Studio)
    @Query("SELECT v FROM Video v WHERE v.channelId = :channelId " +
            "AND LOWER(v.title) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Video> searchByChannelAndTitle(
            @Param("channelId") Long channelId,
            @Param("query") String query,
            Pageable pageable);
}
