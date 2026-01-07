package com.youtube.service;

import com.youtube.entity.*;
import com.youtube.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Like Service - Handle video likes/dislikes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LikeService {

    private final VideoLikeRepository videoLikeRepository;
    private final VideoStatsRepository videoStatsRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Like a video
     */
    @Transactional
    public void likeVideo(Long videoId, Long userId) {
        Optional<VideoLike> existing = videoLikeRepository.findByVideoIdAndUserId(videoId, userId);

        if (existing.isPresent()) {
            VideoLike like = existing.get();
            if (like.getLikeType() == LikeType.LIKE) {
                // Already liked - no action needed
                return;
            }

            // Was dislike, now like - update
            videoStatsRepository.decrementDislikeCount(videoId);
            like.setLikeType(LikeType.LIKE);
            videoLikeRepository.save(like);
            videoStatsRepository.incrementLikeCount(videoId);
        } else {
            // New like
            VideoLike like = VideoLike.builder()
                    .videoId(videoId)
                    .userId(userId)
                    .likeType(LikeType.LIKE)
                    .build();
            videoLikeRepository.save(like);
            videoStatsRepository.incrementLikeCount(videoId);
        }

        // Publish event
        kafkaTemplate.send("like-events", String.valueOf(videoId),
                new LikeEvent(videoId, userId, "LIKE"));

        log.debug("User {} liked video {}", userId, videoId);
    }

    /**
     * Dislike a video
     */
    @Transactional
    public void dislikeVideo(Long videoId, Long userId) {
        Optional<VideoLike> existing = videoLikeRepository.findByVideoIdAndUserId(videoId, userId);

        if (existing.isPresent()) {
            VideoLike like = existing.get();
            if (like.getLikeType() == LikeType.DISLIKE) {
                // Already disliked - no action needed
                return;
            }

            // Was like, now dislike - update
            videoStatsRepository.decrementLikeCount(videoId);
            like.setLikeType(LikeType.DISLIKE);
            videoLikeRepository.save(like);
            videoStatsRepository.incrementDislikeCount(videoId);
        } else {
            // New dislike
            VideoLike like = VideoLike.builder()
                    .videoId(videoId)
                    .userId(userId)
                    .likeType(LikeType.DISLIKE)
                    .build();
            videoLikeRepository.save(like);
            videoStatsRepository.incrementDislikeCount(videoId);
        }

        // Publish event
        kafkaTemplate.send("like-events", String.valueOf(videoId),
                new LikeEvent(videoId, userId, "DISLIKE"));

        log.debug("User {} disliked video {}", userId, videoId);
    }

    /**
     * Remove rating from a video
     */
    @Transactional
    public void removeRating(Long videoId, Long userId) {
        Optional<VideoLike> existing = videoLikeRepository.findByVideoIdAndUserId(videoId, userId);

        if (existing.isPresent()) {
            VideoLike like = existing.get();

            if (like.getLikeType() == LikeType.LIKE) {
                videoStatsRepository.decrementLikeCount(videoId);
            } else {
                videoStatsRepository.decrementDislikeCount(videoId);
            }

            videoLikeRepository.delete(like);

            // Publish event
            kafkaTemplate.send("like-events", String.valueOf(videoId),
                    new LikeEvent(videoId, userId, "REMOVE"));

            log.debug("User {} removed rating from video {}", userId, videoId);
        }
    }

    /**
     * Get user's rating for a video
     */
    public Optional<LikeType> getUserRating(Long videoId, Long userId) {
        return videoLikeRepository.findByVideoIdAndUserId(videoId, userId)
                .map(VideoLike::getLikeType);
    }

    /**
     * Check if user liked a video
     */
    public boolean hasUserLiked(Long videoId, Long userId) {
        return videoLikeRepository.existsByVideoIdAndUserIdAndLikeType(videoId, userId, LikeType.LIKE);
    }

    /**
     * Check if user disliked a video
     */
    public boolean hasUserDisliked(Long videoId, Long userId) {
        return videoLikeRepository.existsByVideoIdAndUserIdAndLikeType(videoId, userId, LikeType.DISLIKE);
    }

    // Simple event record
    private record LikeEvent(Long videoId, Long userId, String action) {
    }
}
