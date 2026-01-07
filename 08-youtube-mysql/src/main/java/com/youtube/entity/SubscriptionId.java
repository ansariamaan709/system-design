package com.youtube.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite key for Subscription
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionId implements Serializable {
    private Long userId;
    private Long channelId;
}
