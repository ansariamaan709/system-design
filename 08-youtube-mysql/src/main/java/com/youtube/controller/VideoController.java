package com.youtube.controller;

import com.youtube.dto.*;
import com.youtube.service.VideoService;
import com.youtube.service.ViewCountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Video Controller - Video CRUD and playback operations
 */
@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final ViewCountService viewCountService;

    /**
     * Upload a new video
     */
    @PostMapping
    public ResponseEntity<VideoResponse> uploadVideo(@Valid @RequestBody VideoUploadRequest request) {
        VideoResponse response = videoService.uploadVideo(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get video by ID
     */
    @GetMapping("/{videoId}")
    public ResponseEntity<VideoResponse> getVideo(@PathVariable Long videoId) {
        return videoService.getVideo(videoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update video metadata
     */
    @PutMapping("/{videoId}")
    public ResponseEntity<VideoResponse> updateVideo(
            @PathVariable Long videoId,
            @Valid @RequestBody VideoUploadRequest request) {
        VideoResponse response = videoService.updateVideo(videoId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete video (soft delete)
     */
    @DeleteMapping("/{videoId}")
    public ResponseEntity<Void> deleteVideo(@PathVariable Long videoId) {
        videoService.deleteVideo(videoId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Publish video (after processing)
     */
    @PostMapping("/{videoId}/publish")
    public ResponseEntity<VideoResponse> publishVideo(@PathVariable Long videoId) {
        VideoResponse response = videoService.publishVideo(videoId);
        return ResponseEntity.ok(response);
    }

    /**
     * Record a view event
     */
    @PostMapping("/{videoId}/view")
    public ResponseEntity<Void> recordView(
            @PathVariable Long videoId,
            @RequestBody(required = false) ViewEvent event) {

        if (event == null) {
            event = new ViewEvent();
        }
        event.setVideoId(videoId);
        event.setTimestamp(System.currentTimeMillis());

        viewCountService.recordView(event);
        return ResponseEntity.accepted().build();
    }

    /**
     * Get real-time view count
     */
    @GetMapping("/{videoId}/views")
    public ResponseEntity<ViewCountResponse> getViewCount(@PathVariable Long videoId) {
        long count = viewCountService.getViewCount(videoId);
        String formatted = viewCountService.formatViewCount(count);
        return ResponseEntity.ok(new ViewCountResponse(videoId, count, formatted));
    }

    /**
     * Batch get videos by IDs
     */
    @GetMapping("/batch")
    public ResponseEntity<List<VideoResponse>> getVideos(@RequestParam List<Long> ids) {
        List<VideoResponse> videos = videoService.getVideosByIds(ids);
        return ResponseEntity.ok(videos);
    }

    // Response class for view count
    public record ViewCountResponse(Long videoId, long viewCount, String formattedViewCount) {
    }
}
