package com.youtube.controller;

import com.youtube.dto.*;
import com.youtube.service.ChannelService;
import com.youtube.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Channel Controller - Channel operations and subscriptions
 */
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final VideoService videoService;

    /**
     * Create a new channel
     */
    @PostMapping
    public ResponseEntity<ChannelResponse> createChannel(@Valid @RequestBody ChannelCreateRequest request) {
        ChannelResponse response = channelService.createChannel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get channel by ID
     */
    @GetMapping("/{channelId}")
    public ResponseEntity<ChannelResponse> getChannel(@PathVariable Long channelId) {
        return channelService.getChannel(channelId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get channel by handle (@username)
     */
    @GetMapping("/handle/{handle}")
    public ResponseEntity<ChannelResponse> getChannelByHandle(@PathVariable String handle) {
        return channelService.getChannelByHandle(handle)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update channel
     */
    @PutMapping("/{channelId}")
    public ResponseEntity<ChannelResponse> updateChannel(
            @PathVariable Long channelId,
            @Valid @RequestBody ChannelCreateRequest request) {
        ChannelResponse response = channelService.updateChannel(channelId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get channel's videos
     */
    @GetMapping("/{channelId}/videos")
    public ResponseEntity<PagedResponse<VideoResponse>> getChannelVideos(
            @PathVariable Long channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<VideoResponse> response = videoService.getChannelVideos(channelId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Search videos within a channel
     */
    @GetMapping("/{channelId}/videos/search")
    public ResponseEntity<PagedResponse<VideoResponse>> searchChannelVideos(
            @PathVariable Long channelId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<VideoResponse> response = videoService.searchChannelVideos(channelId, q, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Subscribe to a channel
     */
    @PostMapping("/{channelId}/subscribe")
    public ResponseEntity<Void> subscribe(
            @PathVariable Long channelId,
            @RequestHeader("X-User-Id") Long userId) {
        channelService.subscribe(userId, channelId);
        return ResponseEntity.ok().build();
    }

    /**
     * Unsubscribe from a channel
     */
    @DeleteMapping("/{channelId}/subscribe")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable Long channelId,
            @RequestHeader("X-User-Id") Long userId) {
        channelService.unsubscribe(userId, channelId);
        return ResponseEntity.ok().build();
    }

    /**
     * Check if user is subscribed
     */
    @GetMapping("/{channelId}/subscribed")
    public ResponseEntity<SubscriptionStatus> isSubscribed(
            @PathVariable Long channelId,
            @RequestHeader("X-User-Id") Long userId) {
        boolean subscribed = channelService.isSubscribed(userId, channelId);
        return ResponseEntity.ok(new SubscriptionStatus(subscribed));
    }

    /**
     * Search channels
     */
    @GetMapping("/search")
    public ResponseEntity<List<ChannelResponse>> searchChannels(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        List<ChannelResponse> results = channelService.searchChannels(q, limit);
        return ResponseEntity.ok(results);
    }

    // Response class for subscription status
    public record SubscriptionStatus(boolean subscribed) {
    }
}
