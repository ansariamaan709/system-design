package com.youtube.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for adding a comment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentRequest {

    @NotNull(message = "Video ID is required")
    private Long videoId;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Comment content is required")
    @Size(max = 10000, message = "Comment must not exceed 10000 characters")
    private String content;

    // Optional: for replies
    private Long parentCommentId;
}
