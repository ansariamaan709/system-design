package com.youtube.controller;

import com.youtube.dto.*;
import com.youtube.service.ChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * User Controller - User-related operations
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final ChannelService channelService;

    /**
     * Get user's subscriptions
     */
    @GetMapping("/me/subscriptions")
    public ResponseEntity<PagedResponse<ChannelResponse>> getSubscriptions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<ChannelResponse> response = channelService.getUserSubscriptions(userId, page, size);
        return ResponseEntity.ok(response);
    }
}
