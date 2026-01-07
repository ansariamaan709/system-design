package com.youtube.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Video format/transcoding output
 * 
 * Stores multiple format versions of each video
 * (360p, 720p, 1080p, 4K, audio-only, etc.)
 */
@Entity
@Table(name = "video_formats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoFormat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false)
    private Long videoId;

    @Column(name = "format_code", nullable = false, length = 20)
    private String formatCode; // "360p", "720p", "1080p", "4k", "audio_only"

    @Column(name = "codec", nullable = false, length = 50)
    private String codec; // "h264", "vp9", "av1"

    @Column(name = "container", nullable = false, length = 20)
    private String container; // "mp4", "webm"

    @Column(name = "bitrate_kbps", nullable = false)
    private Integer bitrateKbps;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "storage_url", nullable = false, length = 512)
    private String storageUrl;

    @Column(name = "cdn_url", length = 512)
    private String cdnUrl;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Get human-readable file size
     */
    public String getFormattedFileSize() {
        if (fileSizeBytes < 1024) {
            return fileSizeBytes + " B";
        } else if (fileSizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", fileSizeBytes / 1024.0);
        } else if (fileSizeBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSizeBytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", fileSizeBytes / (1024.0 * 1024 * 1024));
        }
    }
}
