package com.youtube.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Video like/dislike entity
 * 
 * In Vitess: Sharded by video_id
 */
@Entity
@Table(name = "video_likes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(VideoLikeId.class)
public class VideoLike {

    @Id
    @Column(name = "video_id")
    private Long videoId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "like_type", nullable = false)
    private LikeType likeType;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
