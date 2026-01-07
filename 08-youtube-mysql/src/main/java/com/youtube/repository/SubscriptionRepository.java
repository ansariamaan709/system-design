package com.youtube.repository;

import com.youtube.entity.Subscription;
import com.youtube.entity.SubscriptionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Subscription Repository
 * 
 * In Vitess: Sharded by user_id for "my subscriptions" queries
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, SubscriptionId> {

    // User's subscriptions (routes to user's shard)
    Page<Subscription> findByUserIdOrderBySubscribedAtDesc(Long userId, Pageable pageable);

    // Check if user is subscribed
    boolean existsByUserIdAndChannelId(Long userId, Long channelId);

    // Count user's subscriptions
    long countByUserId(Long userId);

    // Count channel's subscribers (scatter-gather in Vitess)
    long countByChannelId(Long channelId);

    // Get subscribed channel IDs for a user
    @Query("SELECT s.channelId FROM Subscription s WHERE s.userId = :userId")
    List<Long> findChannelIdsByUserId(@Param("userId") Long userId);

    // Recent subscribers for a channel (for notifications)
    @Query("SELECT s FROM Subscription s WHERE s.channelId = :channelId " +
            "ORDER BY s.subscribedAt DESC")
    Page<Subscription> findRecentSubscribers(
            @Param("channelId") Long channelId,
            Pageable pageable);
}
