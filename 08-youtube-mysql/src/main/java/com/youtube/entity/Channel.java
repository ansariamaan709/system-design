package com.youtube.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Channel entity
 * 
 * One user can have multiple channels
 * Channel stats are denormalized for performance
 */
@Entity
@Table(name = "channels")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Channel {

    @Id
    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "handle", unique = true, length = 50)
    private String handle; // @username

    @Column(name = "custom_url", length = 100)
    private String customUrl;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "banner_url", length = 512)
    private String bannerUrl;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    // Denormalized stats (updated asynchronously)
    @Column(name = "subscriber_count")
    @Builder.Default
    private Long subscriberCount = 0L;

    @Column(name = "video_count")
    @Builder.Default
    private Integer videoCount = 0;

    @Column(name = "total_views")
    @Builder.Default
    private Long totalViews = 0L;

    @Column(name = "is_monetized")
    @Builder.Default
    private Boolean isMonetized = false;

    @Column(name = "partner_since")
    private LocalDateTime partnerSince;

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

    /**
     * Format subscriber count for display
     */
    public String getFormattedSubscriberCount() {
        if (subscriberCount < 1000) {
            return String.valueOf(subscriberCount);
        } else if (subscriberCount < 1_000_000) {
            return String.format("%.1fK", subscriberCount / 1000.0);
        } else if (subscriberCount < 1_000_000_000) {
            return String.format("%.1fM", subscriberCount / 1_000_000.0);
        } else {
            return String.format("%.1fB", subscriberCount / 1_000_000_000.0);
        }
    }
}
