package com.youtube.repository;

import com.youtube.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Channel Repository
 */
@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByHandle(String handle);

    Optional<Channel> findByUserId(Long userId);

    List<Channel> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByHandle(String handle);

    // Search channels
    @Query("SELECT c FROM Channel c WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Channel> searchByTitle(@Param("query") String query, org.springframework.data.domain.Pageable pageable);

    // Top channels by subscribers
    @Query("SELECT c FROM Channel c ORDER BY c.subscriberCount DESC")
    List<Channel> findTopBySubscribers(org.springframework.data.domain.Pageable pageable);

    // Increment subscriber count
    @Modifying
    @Transactional
    @Query("UPDATE Channel c SET c.subscriberCount = c.subscriberCount + 1 WHERE c.channelId = :channelId")
    int incrementSubscriberCount(@Param("channelId") Long channelId);

    // Decrement subscriber count
    @Modifying
    @Transactional
    @Query("UPDATE Channel c SET c.subscriberCount = c.subscriberCount - 1 " +
            "WHERE c.channelId = :channelId AND c.subscriberCount > 0")
    int decrementSubscriberCount(@Param("channelId") Long channelId);

    // Update video count
    @Modifying
    @Transactional
    @Query("UPDATE Channel c SET c.videoCount = c.videoCount + 1 WHERE c.channelId = :channelId")
    int incrementVideoCount(@Param("channelId") Long channelId);
}
