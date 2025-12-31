package com.urlshortener.dto;

import com.urlshortener.validation.ValidUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlRequest {

    @NotBlank(message = "Original URL is required")
    @ValidUrl(message = "Invalid URL format")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    private String originalUrl;

    @Size(min = 4, max = 20, message = "Custom alias must be between 4 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Custom alias can only contain alphanumeric characters, hyphens, and underscores")
    private String customAlias;

    private LocalDateTime expiresAt;

    private String userId;
}
