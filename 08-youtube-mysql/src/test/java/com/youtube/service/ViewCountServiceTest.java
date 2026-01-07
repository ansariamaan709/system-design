package com.youtube.service;

import com.youtube.dto.ViewEvent;
import com.youtube.repository.VideoStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewCountServiceTest {

    @Mock
    private VideoStatsRepository videoStatsRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ViewCountService viewCountService;

    @BeforeEach
    void setUp() {
        viewCountService = new ViewCountService(
                videoStatsRepository,
                redisTemplate,
                kafkaTemplate);
    }

    @Test
    void shouldRecordView() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        // When
        viewCountService.recordView(1L, 100L, "192.168.1.1", "Chrome");

        // Then
        verify(kafkaTemplate).send(eq("video-views"), eq("1"), any(ViewEvent.class));
    }

    @Test
    void shouldIncrementRedisCount() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("video:views:1")).thenReturn(500L);

        // When
        viewCountService.incrementViewCount(1L);

        // Then
        verify(valueOperations).increment("video:views:1");
    }

    @Test
    void shouldFlushToMySQLAt1000Views() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("video:views:1")).thenReturn(1000L);
        when(valueOperations.getAndSet("video:views:1", 0L)).thenReturn(1000L);

        // When
        viewCountService.incrementViewCount(1L);

        // Then
        verify(videoStatsRepository).incrementViewCount(1L, 1000L);
    }

    @Test
    void shouldProcessViewEvent() {
        // Given
        ViewEvent event = new ViewEvent();
        event.setVideoId(1L);
        event.setUserId(100L);
        event.setClientIp("192.168.1.1");
        event.setTimestamp(LocalDateTime.now());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("video:views:1")).thenReturn(1L);

        // When
        viewCountService.processViewEvent(event);

        // Then
        verify(valueOperations).increment("video:views:1");
    }
}
