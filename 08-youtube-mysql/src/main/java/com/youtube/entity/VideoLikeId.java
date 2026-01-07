package com.youtube.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite key for VideoLike
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoLikeId implements Serializable {
    private Long videoId;
    private Long userId;
}
