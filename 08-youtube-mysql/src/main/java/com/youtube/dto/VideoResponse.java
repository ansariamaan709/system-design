package com.youtube.dto;

import com.youtube.entity.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO for video response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoResponse {

    private Long videoId;
    private Long channelId;
    private String title;
    private String description;
    private Integer durationSeconds;
    private UploadStatus uploadStatus;
    private Visibility visibility;
    private Integer categoryId;
    private String thumbnailUrl;
    private String previewUrl;
    private Boolean monetizationEnabled;
    private AdSuitability adSuitability;
    private Boolean isLiveStream;
    private Boolean isPremiere;
    private Boolean isShort;
    private Boolean ageRestricted;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    // Stats (from VideoStats)
    private Long viewCount;
    private Long likeCount;
    private Long dislikeCount;
    private Long commentCount;
    private String formattedViewCount;

    // Channel info (optional, for embedding)
    private ChannelSummary channel;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelSummary {
        private Long channelId;
        private String title;
        private String handle;
        private String avatarUrl;
        private Long subscriberCount;
        private String formattedSubscriberCount;
    }

    /**
     * Create from entity
     */
    public static VideoResponse from(Video video, VideoStats stats) {
        return VideoResponse.builder()
                .videoId(video.getVideoId())
                .channelId(video.getChannelId())
                .title(video.getTitle())
                .description(video.getDescription())
                .durationSeconds(video.getDurationSeconds())
                .uploadStatus(video.getUploadStatus())
                .visibility(video.getVisibility())
                .categoryId(video.getCategoryId())
                .thumbnailUrl(video.getThumbnailUrl())
                .previewUrl(video.getPreviewUrl())
                .monetizationEnabled(video.getMonetizationEnabled())
                .adSuitability(video.getAdSuitability())
                .isLiveStream(video.getIsLiveStream())
                .isPremiere(video.getIsPremiere())
                .isShort(video.getIsShort())
                .ageRestricted(video.getAgeRestricted())
                .publishedAt(video.getPublishedAt())
                .createdAt(video.getCreatedAt())
                .viewCount(stats != null ? stats.getViewCount() : 0L)
                .likeCount(stats != null ? stats.getLikeCount() : 0L)
                .dislikeCount(stats != null ? stats.getDislikeCount() : 0L)
                .commentCount(stats != null ? stats.getCommentCount() : 0L)
                .formattedViewCount(stats != null ? stats.getFormattedViewCount() : "0")
                .build();
    }
}
