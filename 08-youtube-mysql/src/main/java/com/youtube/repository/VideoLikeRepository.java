package com.youtube.repository;

import com.youtube.entity.VideoLike;
import com.youtube.entity.VideoLikeId;
import com.youtube.entity.LikeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Video Like Repository
 * 
 * In Vitess: Sharded by video_id
 */
@Repository
public interface VideoLikeRepository extends JpaRepository<VideoLike, VideoLikeId> {

    // Find user's rating on a video
    Optional<VideoLike> findByVideoIdAndUserId(Long videoId, Long userId);

    // Check if user liked/disliked a video
    boolean existsByVideoIdAndUserIdAndLikeType(Long videoId, Long userId, LikeType likeType);

    // Count likes for a video
    long countByVideoIdAndLikeType(Long videoId, LikeType likeType);

    // Delete user's rating
    void deleteByVideoIdAndUserId(Long videoId, Long userId);

    // Check if user has any rating on the video
    boolean existsByVideoIdAndUserId(Long videoId, Long userId);

    // Get user's liked videos (cross-shard query)
    @Query("SELECT vl.videoId FROM VideoLike vl WHERE vl.userId = :userId AND vl.likeType = 'LIKE' " +
            "ORDER BY vl.createdAt DESC")
    java.util.List<Long> findLikedVideoIdsByUser(@Param("userId") Long userId,
            org.springframework.data.domain.Pageable pageable);
}
