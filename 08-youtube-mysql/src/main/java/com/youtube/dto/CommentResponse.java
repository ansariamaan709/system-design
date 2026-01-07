package com.youtube.dto;

import com.youtube.entity.Comment;
import com.youtube.entity.CommentStatus;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO for comment response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    private Long commentId;
    private Long videoId;
    private Long userId;
    private Long parentCommentId;
    private String content;
    private Integer likeCount;
    private Integer replyCount;
    private CommentStatus status;
    private Boolean isPinned;
    private Boolean isHearted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // User info (for display)
    private UserSummary author;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        private Long userId;
        private String displayName;
        private String profileImageUrl;
    }

    /**
     * Create from entity
     */
    public static CommentResponse from(Comment comment) {
        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .videoId(comment.getVideoId())
                .userId(comment.getUserId())
                .parentCommentId(comment.getParentCommentId())
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .replyCount(comment.getReplyCount())
                .status(comment.getStatus())
                .isPinned(comment.getIsPinned())
                .isHearted(comment.getIsHearted())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
