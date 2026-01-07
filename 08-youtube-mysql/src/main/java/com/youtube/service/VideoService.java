package com.youtube.service;

import com.youtube.dto.*;
import com.youtube.entity.*;
import com.youtube.id.SnowflakeIdGenerator;
import com.youtube.repository.*;
import com.youtube.event.VideoUploadEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Video Service - Core video operations
 * 
 * Handles video CRUD, with Vitess-optimized query patterns
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoService {

    private final VideoRepository videoRepository;
    private final VideoStatsRepository videoStatsRepository;
    private final ChannelRepository channelRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Upload new video (creates metadata, triggers processing)
     */
    @Transactional
    public VideoResponse uploadVideo(VideoUploadRequest request) {
        // Validate channel exists
        Channel channel = channelRepository.findById(request.getChannelId())
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + request.getChannelId()));

        // Generate unique video ID
        long videoId = idGenerator.nextId();

        // Create video entity
        Video video = Video.builder()
                .videoId(videoId)
                .channelId(request.getChannelId())
                .title(request.getTitle())
                .description(request.getDescription())
                .categoryId(request.getCategoryId())
                .defaultLanguage(request.getDefaultLanguage() != null ? request.getDefaultLanguage() : "en")
                .visibility(parseVisibility(request.getVisibility()))
                .monetizationEnabled(
                        request.getMonetizationEnabled() != null ? request.getMonetizationEnabled() : false)
                .isShort(request.getIsShort() != null ? request.getIsShort() : false)
                .ageRestricted(request.getAgeRestricted() != null ? request.getAgeRestricted() : false)
                .uploadStatus(UploadStatus.PROCESSING)
                .build();

        video = videoRepository.save(video);

        // Create stats entry
        VideoStats stats = VideoStats.builder()
                .videoId(videoId)
                .build();
        videoStatsRepository.save(stats);

        // Publish event for async processing (transcoding, thumbnail generation, etc.)
        VideoUploadEvent event = VideoUploadEvent.builder()
                .videoId(videoId)
                .channelId(request.getChannelId())
                .sourceUrl(request.getSourceUrl())
                .timestamp(System.currentTimeMillis())
                .build();
        kafkaTemplate.send("video-uploads", String.valueOf(videoId), event);

        log.info("Video uploaded: {} for channel {}", videoId, request.getChannelId());

        return VideoResponse.from(video, stats);
    }

    /**
     * Get video by ID (point lookup - optimal for Vitess)
     */
    @Cacheable(value = "videos", key = "#videoId")
    public Optional<VideoResponse> getVideo(Long videoId) {
        return videoRepository.findById(videoId)
                .map(video -> {
                    VideoStats stats = videoStatsRepository.findById(videoId).orElse(null);
                    return VideoResponse.from(video, stats);
                });
    }

    /**
     * Get videos by channel (uses lookup vindex in Vitess)
     */
    @Cacheable(value = "channel_videos", key = "#channelId + ':' + #page + ':' + #size")
    public PagedResponse<VideoResponse> getChannelVideos(Long channelId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Video> videoPage = videoRepository.findByChannelIdAndUploadStatusOrderByPublishedAtDesc(
                channelId, UploadStatus.PUBLISHED, pageable);

        List<Long> videoIds = videoPage.getContent().stream()
                .map(Video::getVideoId)
                .collect(Collectors.toList());

        // Batch fetch stats
        List<VideoStats> statsList = videoStatsRepository.findByVideoIds(videoIds);
        java.util.Map<Long, VideoStats> statsMap = statsList.stream()
                .collect(Collectors.toMap(VideoStats::getVideoId, s -> s));

        List<VideoResponse> responses = videoPage.getContent().stream()
                .map(v -> VideoResponse.from(v, statsMap.get(v.getVideoId())))
                .collect(Collectors.toList());

        return PagedResponse.of(responses, page, size, videoPage.getTotalElements());
    }

    /**
     * Update video metadata
     */
    @Transactional
    @CacheEvict(value = { "videos", "channel_videos" }, allEntries = true)
    public VideoResponse updateVideo(Long videoId, VideoUploadRequest request) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + videoId));

        if (request.getTitle() != null) {
            video.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            video.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            video.setVisibility(parseVisibility(request.getVisibility()));
        }
        if (request.getCategoryId() != null) {
            video.setCategoryId(request.getCategoryId());
        }
        if (request.getMonetizationEnabled() != null) {
            video.setMonetizationEnabled(request.getMonetizationEnabled());
        }
        if (request.getAgeRestricted() != null) {
            video.setAgeRestricted(request.getAgeRestricted());
        }

        video = videoRepository.save(video);

        // Publish update event for cache invalidation
        kafkaTemplate.send("video-updates", String.valueOf(videoId), video);

        VideoStats stats = videoStatsRepository.findById(videoId).orElse(null);
        return VideoResponse.from(video, stats);
    }

    /**
     * Publish video (make it public after processing)
     */
    @Transactional
    @CacheEvict(value = { "videos", "channel_videos" }, allEntries = true)
    public VideoResponse publishVideo(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + videoId));

        video.setUploadStatus(UploadStatus.PUBLISHED);
        video.setPublishedAt(LocalDateTime.now());
        video = videoRepository.save(video);

        // Update channel video count
        channelRepository.incrementVideoCount(video.getChannelId());

        VideoStats stats = videoStatsRepository.findById(videoId).orElse(null);
        return VideoResponse.from(video, stats);
    }

    /**
     * Delete video (soft delete)
     */
    @Transactional
    @CacheEvict(value = { "videos", "channel_videos" }, allEntries = true)
    public void deleteVideo(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + videoId));

        video.setUploadStatus(UploadStatus.DELETED);
        videoRepository.save(video);

        log.info("Video deleted: {}", videoId);
    }

    /**
     * Get batch of videos by IDs (routes to specific shards)
     */
    public List<VideoResponse> getVideosByIds(List<Long> videoIds) {
        List<Video> videos = videoRepository.findByVideoIds(videoIds);
        List<VideoStats> statsList = videoStatsRepository.findByVideoIds(videoIds);

        java.util.Map<Long, VideoStats> statsMap = statsList.stream()
                .collect(Collectors.toMap(VideoStats::getVideoId, s -> s));

        return videos.stream()
                .map(v -> VideoResponse.from(v, statsMap.get(v.getVideoId())))
                .collect(Collectors.toList());
    }

    /**
     * Search videos by title within a channel
     */
    public PagedResponse<VideoResponse> searchChannelVideos(Long channelId, String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Video> results = videoRepository.searchByChannelAndTitle(channelId, query, pageable);

        List<VideoResponse> responses = results.getContent().stream()
                .map(v -> {
                    VideoStats stats = videoStatsRepository.findById(v.getVideoId()).orElse(null);
                    return VideoResponse.from(v, stats);
                })
                .collect(Collectors.toList());

        return PagedResponse.of(responses, page, size, results.getTotalElements());
    }

    private Visibility parseVisibility(String visibility) {
        if (visibility == null)
            return Visibility.PUBLIC;
        try {
            return Visibility.valueOf(visibility.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Visibility.PUBLIC;
        }
    }
}
