package com.youtube.dto;

import com.youtube.entity.Channel;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO for channel response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResponse {

    private Long channelId;
    private Long userId;
    private String handle;
    private String customUrl;
    private String title;
    private String description;
    private String bannerUrl;
    private String avatarUrl;
    private Long subscriberCount;
    private String formattedSubscriberCount;
    private Integer videoCount;
    private Long totalViews;
    private Boolean isMonetized;
    private LocalDateTime partnerSince;
    private LocalDateTime createdAt;

    /**
     * Create from entity
     */
    public static ChannelResponse from(Channel channel) {
        return ChannelResponse.builder()
                .channelId(channel.getChannelId())
                .userId(channel.getUserId())
                .handle(channel.getHandle())
                .customUrl(channel.getCustomUrl())
                .title(channel.getTitle())
                .description(channel.getDescription())
                .bannerUrl(channel.getBannerUrl())
                .avatarUrl(channel.getAvatarUrl())
                .subscriberCount(channel.getSubscriberCount())
                .formattedSubscriberCount(channel.getFormattedSubscriberCount())
                .videoCount(channel.getVideoCount())
                .totalViews(channel.getTotalViews())
                .isMonetized(channel.getIsMonetized())
                .partnerSince(channel.getPartnerSince())
                .createdAt(channel.getCreatedAt())
                .build();
    }
}
