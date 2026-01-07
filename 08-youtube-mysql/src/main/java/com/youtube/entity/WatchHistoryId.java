package com.youtube.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite key for WatchHistory
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchHistoryId implements Serializable {
    private Long userId;
    private Long videoId;
}
