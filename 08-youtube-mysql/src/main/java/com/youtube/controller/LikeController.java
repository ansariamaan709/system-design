package com.youtube.controller;

import com.youtube.entity.LikeType;
import com.youtube.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Like Controller - Video rating operations
 */
@RestController
@RequestMapping("/api/v1/videos/{videoId}")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /**
     * Like a video
     */
    @PostMapping("/like")
    public ResponseEntity<Void> likeVideo(
            @PathVariable Long videoId,
            @RequestHeader("X-User-Id") Long userId) {
        likeService.likeVideo(videoId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Dislike a video
     */
    @PostMapping("/dislike")
    public ResponseEntity<Void> dislikeVideo(
            @PathVariable Long videoId,
            @RequestHeader("X-User-Id") Long userId) {
        likeService.dislikeVideo(videoId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Remove rating from a video
     */
    @DeleteMapping("/rating")
    public ResponseEntity<Void> removeRating(
            @PathVariable Long videoId,
            @RequestHeader("X-User-Id") Long userId) {
        likeService.removeRating(videoId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Get user's rating for a video
     */
    @GetMapping("/rating")
    public ResponseEntity<RatingResponse> getUserRating(
            @PathVariable Long videoId,
            @RequestHeader("X-User-Id") Long userId) {
        Optional<LikeType> rating = likeService.getUserRating(videoId, userId);
        return ResponseEntity.ok(new RatingResponse(
                videoId,
                userId,
                rating.map(LikeType::name).orElse(null)));
    }

    // Response class for rating
    public record RatingResponse(Long videoId, Long userId, String rating) {
    }
}
