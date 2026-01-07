package com.youtube.controller;

import com.youtube.dto.*;
import com.youtube.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Comment Controller - Comment operations
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * Add a comment to a video
     */
    @PostMapping("/videos/{videoId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long videoId,
            @Valid @RequestBody CommentRequest request) {
        request.setVideoId(videoId);
        CommentResponse response = commentService.addComment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get comments for a video (newest first)
     */
    @GetMapping("/videos/{videoId}/comments")
    public ResponseEntity<PagedResponse<CommentResponse>> getComments(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort) {

        PagedResponse<CommentResponse> response;
        if ("top".equals(sort)) {
            response = commentService.getTopComments(videoId, page, size);
        } else {
            response = commentService.getVideoComments(videoId, page, size);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get pinned comment for a video
     */
    @GetMapping("/videos/{videoId}/comments/pinned")
    public ResponseEntity<CommentResponse> getPinnedComment(@PathVariable Long videoId) {
        return commentService.getPinnedComment(videoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get replies to a comment
     */
    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<PagedResponse<CommentResponse>> getReplies(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponse<CommentResponse> response = commentService.getReplies(commentId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Like a comment
     */
    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<Void> likeComment(@PathVariable Long commentId) {
        commentService.likeComment(commentId);
        return ResponseEntity.ok().build();
    }

    /**
     * Heart a comment (creator action)
     */
    @PostMapping("/comments/{commentId}/heart")
    public ResponseEntity<Void> heartComment(@PathVariable Long commentId) {
        commentService.heartComment(commentId);
        return ResponseEntity.ok().build();
    }

    /**
     * Pin a comment (creator action)
     */
    @PostMapping("/videos/{videoId}/comments/{commentId}/pin")
    public ResponseEntity<Void> pinComment(
            @PathVariable Long videoId,
            @PathVariable Long commentId) {
        commentService.pinComment(videoId, commentId);
        return ResponseEntity.ok().build();
    }

    /**
     * Delete a comment
     */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }
}
