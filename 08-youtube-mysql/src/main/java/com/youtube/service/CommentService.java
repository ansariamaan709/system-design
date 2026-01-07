package com.youtube.service;

import com.youtube.dto.*;
import com.youtube.entity.*;
import com.youtube.id.SnowflakeIdGenerator;
import com.youtube.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Comment Service
 * 
 * Handles video comments with efficient sharding by video_id
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final VideoStatsRepository videoStatsRepository;
    private final UserRepository userRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Add a comment to a video
     */
    @Transactional
    @CacheEvict(value = "video_comments", key = "#request.videoId")
    public CommentResponse addComment(CommentRequest request) {
        // Validate user exists
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getUserId()));

        long commentId = idGenerator.nextId();

        Comment comment = Comment.builder()
                .commentId(commentId)
                .videoId(request.getVideoId())
                .userId(request.getUserId())
                .parentCommentId(request.getParentCommentId())
                .content(request.getContent())
                .build();

        comment = commentRepository.save(comment);

        // Update parent reply count if this is a reply
        if (request.getParentCommentId() != null) {
            commentRepository.incrementReplyCount(request.getParentCommentId());
        }

        // Update video comment count
        videoStatsRepository.incrementCommentCount(request.getVideoId());

        // Publish event for moderation
        kafkaTemplate.send("comment-events", String.valueOf(request.getVideoId()), comment);

        CommentResponse response = CommentResponse.from(comment);
        response.setAuthor(CommentResponse.UserSummary.builder()
                .userId(user.getUserId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .build());

        log.info("Comment {} added to video {}", commentId, request.getVideoId());

        return response;
    }

    /**
     * Get comments for a video (sorted by newest)
     */
    public PagedResponse<CommentResponse> getVideoComments(Long videoId, int page, int size) {
        Page<Comment> comments = commentRepository
                .findByVideoIdAndParentCommentIdIsNullAndStatusOrderByCreatedAtDesc(
                        videoId, CommentStatus.VISIBLE, PageRequest.of(page, size));

        List<CommentResponse> responses = comments.getContent().stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());

        // Batch fetch user info
        enrichWithUserInfo(responses);

        return PagedResponse.of(responses, page, size, comments.getTotalElements());
    }

    /**
     * Get top comments for a video (sorted by likes)
     */
    public PagedResponse<CommentResponse> getTopComments(Long videoId, int page, int size) {
        Page<Comment> comments = commentRepository.findTopCommentsByVideo(
                videoId, CommentStatus.VISIBLE, PageRequest.of(page, size));

        List<CommentResponse> responses = comments.getContent().stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());

        enrichWithUserInfo(responses);

        return PagedResponse.of(responses, page, size, comments.getTotalElements());
    }

    /**
     * Get replies to a comment
     */
    public PagedResponse<CommentResponse> getReplies(Long parentCommentId, int page, int size) {
        Page<Comment> replies = commentRepository
                .findByParentCommentIdAndStatusOrderByCreatedAtAsc(
                        parentCommentId, CommentStatus.VISIBLE, PageRequest.of(page, size));

        List<CommentResponse> responses = replies.getContent().stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());

        enrichWithUserInfo(responses);

        return PagedResponse.of(responses, page, size, replies.getTotalElements());
    }

    /**
     * Like a comment
     */
    @Transactional
    public void likeComment(Long commentId) {
        commentRepository.incrementLikeCount(commentId);
    }

    /**
     * Heart a comment (creator action)
     */
    @Transactional
    public void heartComment(Long commentId) {
        commentRepository.heartComment(commentId);
    }

    /**
     * Pin a comment (creator action)
     */
    @Transactional
    @CacheEvict(value = "video_comments", allEntries = true)
    public void pinComment(Long videoId, Long commentId) {
        // Unpin any existing pinned comment
        commentRepository.unpinAllComments(videoId);

        // Pin the new comment
        commentRepository.pinComment(commentId);
    }

    /**
     * Delete a comment (soft delete)
     */
    @Transactional
    @CacheEvict(value = "video_comments", allEntries = true)
    public void deleteComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found: " + commentId));

        comment.setStatus(CommentStatus.DELETED);
        commentRepository.save(comment);

        log.info("Comment {} deleted", commentId);
    }

    /**
     * Get pinned comment for a video
     */
    public Optional<CommentResponse> getPinnedComment(Long videoId) {
        List<Comment> pinned = commentRepository.findPinnedComment(videoId);
        if (pinned.isEmpty()) {
            return Optional.empty();
        }

        CommentResponse response = CommentResponse.from(pinned.get(0));
        enrichWithUserInfo(List.of(response));

        return Optional.of(response);
    }

    /**
     * Enrich comments with user information
     */
    private void enrichWithUserInfo(List<CommentResponse> comments) {
        List<Long> userIds = comments.stream()
                .map(CommentResponse::getUserId)
                .distinct()
                .collect(Collectors.toList());

        List<User> users = userRepository.findAllById(userIds);
        java.util.Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));

        for (CommentResponse comment : comments) {
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                comment.setAuthor(CommentResponse.UserSummary.builder()
                        .userId(user.getUserId())
                        .displayName(user.getDisplayName())
                        .profileImageUrl(user.getProfileImageUrl())
                        .build());
            }
        }
    }
}
