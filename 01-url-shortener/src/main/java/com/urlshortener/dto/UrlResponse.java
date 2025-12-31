package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlResponse {

    private Long id;
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private Long clickCount;
    private Boolean isActive;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Boolean isCustomAlias;
}
