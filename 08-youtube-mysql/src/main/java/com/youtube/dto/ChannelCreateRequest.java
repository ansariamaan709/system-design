package com.youtube.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for channel creation request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCreateRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must not exceed 100 characters")
    private String title;

    @Pattern(regexp = "^@[a-zA-Z0-9_]{3,30}$", message = "Handle must start with @ and be 3-30 alphanumeric characters")
    private String handle;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;
}
