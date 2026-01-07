package com.youtube.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Subscription entity - User to Channel relationship
 * 
 * In Vitess: Sharded by user_id for "my subscriptions" queries
 * Secondary index on channel_id for "channel subscribers" queries
 */
@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(SubscriptionId.class)
public class Subscription {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "channel_id")
    private Long channelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_level")
    @Builder.Default
    private NotificationLevel notificationLevel = NotificationLevel.PERSONALIZED;

    @Column(name = "subscribed_at")
    @Builder.Default
    private LocalDateTime subscribedAt = LocalDateTime.now();
}
