package com.youtube.dto;

import lombok.*;

/**
 * DTO for view event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewEvent {
    private Long videoId;
    private Long userId; // null for anonymous views
    private Integer watchDurationSeconds;
    private Double percentageWatched;
    private String clientIp;
    private String userAgent;
    private String country;
    private Long timestamp;
}
