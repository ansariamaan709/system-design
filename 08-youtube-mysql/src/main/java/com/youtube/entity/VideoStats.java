package com.youtube.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Video statistics - Denormalized counters
 * 
 * Updated asynchronously via write-behind caching
 * to avoid write amplification on every view
 */
@Entity
@Table(name = "video_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoStats {

    @Id
    @Column(name = "video_id")
    private Long videoId;

    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @Column(name = "like_count")
    @Builder.Default
    private Long likeCount = 0L;

    @Column(name = "dislike_count")
    @Builder.Default
    private Long dislikeCount = 0L;

    @Column(name = "comment_count")
    @Builder.Default
    private Long commentCount = 0L;

    @Column(name = "share_count")
    @Builder.Default
    private Long shareCount = 0L;

    @Column(name = "avg_view_duration_seconds")
    @Builder.Default
    private Integer avgViewDurationSeconds = 0;

    @Column(name = "avg_percentage_viewed", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal avgPercentageViewed = BigDecimal.ZERO;

    @Column(name = "estimated_revenue_micros")
    @Builder.Default
    private Long estimatedRevenueMicros = 0L;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Format view count for display
     */
    public String getFormattedViewCount() {
        if (viewCount < 1000) {
            return String.valueOf(viewCount);
        } else if (viewCount < 1_000_000) {
            return String.format("%.1fK", viewCount / 1000.0);
        } else if (viewCount < 1_000_000_000) {
            return String.format("%.1fM", viewCount / 1_000_000.0);
        } else {
            return String.format("%.1fB", viewCount / 1_000_000_000.0);
        }
    }
}
