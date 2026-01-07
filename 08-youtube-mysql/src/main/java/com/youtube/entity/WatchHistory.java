package com.youtube.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Watch history - User viewing history
 * 
 * In Vitess: Sharded by user_id
 * Used for recommendations, "continue watching", etc.
 */
@Entity
@Table(name = "watch_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(WatchHistoryId.class)
public class WatchHistory {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "video_id")
    private Long videoId;

    @Column(name = "watched_at")
    @Builder.Default
    private LocalDateTime watchedAt = LocalDateTime.now();

    @Column(name = "watch_duration_seconds")
    @Builder.Default
    private Integer watchDurationSeconds = 0;

    @Column(name = "percentage_watched", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal percentageWatched = BigDecimal.ZERO;
}
