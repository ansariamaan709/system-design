package com.youtube.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Video entity - Core content table
 * 
 * In Vitess: Sharded by video_id using xxhash64 vindex
 * 256 shards for even distribution
 */
@Entity
@Table(name = "videos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Video {

    @Id
    @Column(name = "video_id")
    private Long videoId;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_seconds")
    @Builder.Default
    private Integer durationSeconds = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status")
    @Builder.Default
    private UploadStatus uploadStatus = UploadStatus.PROCESSING;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility")
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "default_language", length = 10)
    @Builder.Default
    private String defaultLanguage = "en";

    @Column(name = "thumbnail_url", length = 512)
    private String thumbnailUrl;

    @Column(name = "preview_url", length = 512)
    private String previewUrl;

    @Column(name = "monetization_enabled")
    @Builder.Default
    private Boolean monetizationEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "ad_suitability")
    @Builder.Default
    private AdSuitability adSuitability = AdSuitability.FULL;

    @Column(name = "is_live_stream")
    @Builder.Default
    private Boolean isLiveStream = false;

    @Column(name = "is_premiere")
    @Builder.Default
    private Boolean isPremiere = false;

    @Column(name = "is_short")
    @Builder.Default
    private Boolean isShort = false;

    @Column(name = "age_restricted")
    @Builder.Default
    private Boolean ageRestricted = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
