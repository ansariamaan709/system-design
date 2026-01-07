package com.youtube.service;

import com.youtube.dto.*;
import com.youtube.entity.*;
import com.youtube.id.SnowflakeIdGenerator;
import com.youtube.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Channel Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Create a new channel
     */
    @Transactional
    public ChannelResponse createChannel(ChannelCreateRequest request) {
        // Validate user exists
        userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getUserId()));

        // Validate handle uniqueness
        if (request.getHandle() != null && channelRepository.existsByHandle(request.getHandle())) {
            throw new IllegalArgumentException("Handle already taken: " + request.getHandle());
        }

        long channelId = idGenerator.nextId();

        Channel channel = Channel.builder()
                .channelId(channelId)
                .userId(request.getUserId())
                .title(request.getTitle())
                .handle(request.getHandle())
                .description(request.getDescription())
                .build();

        channel = channelRepository.save(channel);

        log.info("Channel created: {} for user {}", channelId, request.getUserId());

        return ChannelResponse.from(channel);
    }

    /**
     * Get channel by ID
     */
    @Cacheable(value = "channels", key = "#channelId")
    public Optional<ChannelResponse> getChannel(Long channelId) {
        return channelRepository.findById(channelId)
                .map(ChannelResponse::from);
    }

    /**
     * Get channel by handle (@username)
     */
    @Cacheable(value = "channels_by_handle", key = "#handle")
    public Optional<ChannelResponse> getChannelByHandle(String handle) {
        return channelRepository.findByHandle(handle)
                .map(ChannelResponse::from);
    }

    /**
     * Update channel
     */
    @Transactional
    @CacheEvict(value = { "channels", "channels_by_handle" }, allEntries = true)
    public ChannelResponse updateChannel(Long channelId, ChannelCreateRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));

        if (request.getTitle() != null) {
            channel.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            channel.setDescription(request.getDescription());
        }
        if (request.getHandle() != null && !request.getHandle().equals(channel.getHandle())) {
            if (channelRepository.existsByHandle(request.getHandle())) {
                throw new IllegalArgumentException("Handle already taken: " + request.getHandle());
            }
            channel.setHandle(request.getHandle());
        }

        channel = channelRepository.save(channel);
        return ChannelResponse.from(channel);
    }

    /**
     * Subscribe to a channel
     */
    @Transactional
    @CacheEvict(value = { "channels", "user_subscriptions" }, allEntries = true)
    public void subscribe(Long userId, Long channelId) {
        // Check if already subscribed
        if (subscriptionRepository.existsByUserIdAndChannelId(userId, channelId)) {
            return; // Already subscribed
        }

        Subscription subscription = Subscription.builder()
                .userId(userId)
                .channelId(channelId)
                .notificationLevel(NotificationLevel.PERSONALIZED)
                .build();

        subscriptionRepository.save(subscription);

        // Update channel subscriber count
        channelRepository.incrementSubscriberCount(channelId);

        // Publish event
        kafkaTemplate.send("subscription-events", String.valueOf(channelId),
                new SubscriptionEvent(userId, channelId, "SUBSCRIBE"));

        log.info("User {} subscribed to channel {}", userId, channelId);
    }

    /**
     * Unsubscribe from a channel
     */
    @Transactional
    @CacheEvict(value = { "channels", "user_subscriptions" }, allEntries = true)
    public void unsubscribe(Long userId, Long channelId) {
        subscriptionRepository.deleteById(new SubscriptionId(userId, channelId));
        channelRepository.decrementSubscriberCount(channelId);

        // Publish event
        kafkaTemplate.send("subscription-events", String.valueOf(channelId),
                new SubscriptionEvent(userId, channelId, "UNSUBSCRIBE"));

        log.info("User {} unsubscribed from channel {}", userId, channelId);
    }

    /**
     * Get user's subscriptions
     */
    @Cacheable(value = "user_subscriptions", key = "#userId + ':' + #page")
    public PagedResponse<ChannelResponse> getUserSubscriptions(Long userId, int page, int size) {
        Page<Subscription> subscriptions = subscriptionRepository
                .findByUserIdOrderBySubscribedAtDesc(userId, PageRequest.of(page, size));

        List<Long> channelIds = subscriptions.getContent().stream()
                .map(Subscription::getChannelId)
                .collect(Collectors.toList());

        List<Channel> channels = channelRepository.findAllById(channelIds);

        List<ChannelResponse> responses = channels.stream()
                .map(ChannelResponse::from)
                .collect(Collectors.toList());

        return PagedResponse.of(responses, page, size, subscriptions.getTotalElements());
    }

    /**
     * Check if user is subscribed to a channel
     */
    public boolean isSubscribed(Long userId, Long channelId) {
        return subscriptionRepository.existsByUserIdAndChannelId(userId, channelId);
    }

    /**
     * Search channels
     */
    public List<ChannelResponse> searchChannels(String query, int limit) {
        return channelRepository.searchByTitle(query, PageRequest.of(0, limit)).stream()
                .map(ChannelResponse::from)
                .collect(Collectors.toList());
    }

    // Simple event record
    private record SubscriptionEvent(Long userId, Long channelId, String action) {
    }
}
