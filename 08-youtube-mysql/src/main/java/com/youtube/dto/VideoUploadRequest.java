package com.youtube.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for video upload request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoUploadRequest {

    @NotNull(message = "Channel ID is required")
    private Long channelId;

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private Integer categoryId;

    private String defaultLanguage;

    private String visibility; // PUBLIC, PRIVATE, UNLISTED

    private Boolean monetizationEnabled;

    private Boolean isShort;

    private Boolean ageRestricted;

    // Source file URL for transcoding
    private String sourceUrl;
}
