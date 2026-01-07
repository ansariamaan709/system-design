package com.youtube.repository;

import com.youtube.entity.Comment;
import com.youtube.entity.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Comment Repository
 * 
 * In Vitess: Sharded by video_id for efficient video page loads
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Top-level comments for a video (newest first)
    Page<Comment> findByVideoIdAndParentCommentIdIsNullAndStatusOrderByCreatedAtDesc(
            Long videoId,
            CommentStatus status,
            Pageable pageable);

    // Top-level comments for a video (most liked)
    @Query("SELECT c FROM Comment c WHERE c.videoId = :videoId " +
            "AND c.parentCommentId IS NULL AND c.status = :status " +
            "ORDER BY c.likeCount DESC, c.createdAt DESC")
    Page<Comment> findTopCommentsByVideo(
            @Param("videoId") Long videoId,
            @Param("status") CommentStatus status,
            Pageable pageable);

    // Replies to a comment
    Page<Comment> findByParentCommentIdAndStatusOrderByCreatedAtAsc(
            Long parentCommentId,
            CommentStatus status,
            Pageable pageable);

    // Count comments for a video
    long countByVideoIdAndStatus(Long videoId, CommentStatus status);

    // User's comments (cross-shard query in Vitess)
    Page<Comment> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            CommentStatus status,
            Pageable pageable);

    // Pinned comment for a video
    @Query("SELECT c FROM Comment c WHERE c.videoId = :videoId " +
            "AND c.isPinned = true AND c.status = 'VISIBLE'")
    List<Comment> findPinnedComment(@Param("videoId") Long videoId);

    // Increment like count
    @Modifying
    @Transactional
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount + 1 WHERE c.commentId = :commentId")
    int incrementLikeCount(@Param("commentId") Long commentId);

    // Increment reply count
    @Modifying
    @Transactional
    @Query("UPDATE Comment c SET c.replyCount = c.replyCount + 1 WHERE c.commentId = :commentId")
    int incrementReplyCount(@Param("commentId") Long commentId);

    // Heart a comment (creator action)
    @Modifying
    @Transactional
    @Query("UPDATE Comment c SET c.isHearted = true WHERE c.commentId = :commentId")
    int heartComment(@Param("commentId") Long commentId);

    // Pin a comment
    @Modifying
    @Transactional
    @Query("UPDATE Comment c SET c.isPinned = true WHERE c.commentId = :commentId")
    int pinComment(@Param("commentId") Long commentId);

    // Unpin all comments for a video (before pinning new one)
    @Modifying
    @Transactional
    @Query("UPDATE Comment c SET c.isPinned = false WHERE c.videoId = :videoId")
    int unpinAllComments(@Param("videoId") Long videoId);
}
