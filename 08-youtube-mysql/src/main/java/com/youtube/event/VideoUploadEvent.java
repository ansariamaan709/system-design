package com.youtube.event;

import lombok.*;

/**
 * Event for video uploads
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoUploadEvent {
    private Long videoId;
    private Long channelId;
    private String sourceUrl;
    private Long timestamp;
}
