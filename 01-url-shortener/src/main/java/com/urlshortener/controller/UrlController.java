package com.urlshortener.controller;

import com.urlshortener.dto.*;
import com.urlshortener.service.ClickEventService;
import com.urlshortener.service.UrlService;
import com.urlshortener.service.UserAgentParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "URL Shortener", description = "URL shortening and redirection APIs")
public class UrlController {

    private final UrlService urlService;
    private final ClickEventService clickEventService;
    private final UserAgentParser userAgentParser;

    @PostMapping("/api/v1/urls")
    @Operation(summary = "Create a short URL", description = "Creates a shortened URL from a long URL")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "URL created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Custom alias already exists")
    })
    public ResponseEntity<ApiResponse<UrlResponse>> createUrl(
            @Valid @RequestBody CreateUrlRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        if (userId != null) {
            request.setUserId(userId);
        }

        UrlResponse response = urlService.createShortUrl(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "URL shortened successfully"));
    }

    @GetMapping("/{shortCode}")
    @Operation(summary = "Redirect to original URL", description = "Redirects short URL to its original destination")
    public ResponseEntity<Void> redirectUrl(
            @Parameter(description = "Short code of the URL") @PathVariable String shortCode,
            HttpServletRequest request) {

        String originalUrl = urlService.resolveUrl(shortCode);

        // Record click event asynchronously
        recordClick(shortCode, request);

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    @GetMapping("/api/v1/urls/{shortCode}")
    @Operation(summary = "Get URL information", description = "Retrieves information about a shortened URL")
    public ResponseEntity<ApiResponse<UrlResponse>> getUrlInfo(
            @PathVariable String shortCode) {

        UrlResponse response = urlService.getUrlInfo(shortCode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/urls/{shortCode}/stats")
    @Operation(summary = "Get URL statistics", description = "Retrieves click statistics for a shortened URL")
    public ResponseEntity<ApiResponse<UrlStatsResponse>> getUrlStats(
            @PathVariable String shortCode) {

        UrlStatsResponse stats = urlService.getUrlStats(shortCode);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @DeleteMapping("/api/v1/urls/{shortCode}")
    @Operation(summary = "Delete a URL", description = "Deactivates a shortened URL")
    public ResponseEntity<ApiResponse<Void>> deleteUrl(
            @PathVariable String shortCode,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        urlService.deleteUrl(shortCode, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "URL deleted successfully"));
    }

    @GetMapping("/api/v1/users/{userId}/urls")
    @Operation(summary = "Get user's URLs", description = "Retrieves all URLs created by a user")
    public ResponseEntity<ApiResponse<List<UrlResponse>>> getUserUrls(
            @PathVariable String userId) {

        List<UrlResponse> urls = urlService.getUserUrls(userId);
        return ResponseEntity.ok(ApiResponse.success(urls));
    }

    private void recordClick(String shortCode, HttpServletRequest request) {
        try {
            String userAgent = request.getHeader("User-Agent");

            ClickEventDto clickEvent = ClickEventDto.builder()
                    .shortCode(shortCode)
                    .ipAddress(userAgentParser.getClientIp(request))
                    .userAgent(userAgent)
                    .referrer(request.getHeader("Referer"))
                    .browser(userAgentParser.getBrowser(userAgent))
                    .os(userAgentParser.getOperatingSystem(userAgent))
                    .deviceType(userAgentParser.getDeviceType(userAgent))
                    .build();

            clickEventService.recordClickEvent(clickEvent);
        } catch (Exception e) {
            log.error("Failed to record click event", e);
        }
    }
}
