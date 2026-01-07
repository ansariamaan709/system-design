package com.youtube.service;

import com.youtube.dto.VideoResponse;
import com.youtube.dto.VideoUploadRequest;
import com.youtube.entity.Channel;
import com.youtube.entity.User;
import com.youtube.entity.Video;
import com.youtube.entity.VideoStats;
import com.youtube.entity.enums.AccountStatus;
import com.youtube.entity.enums.UploadStatus;
import com.youtube.entity.enums.Visibility;
import com.youtube.id.SnowflakeIdGenerator;
import com.youtube.repository.ChannelRepository;
import com.youtube.repository.VideoRepository;
import com.youtube.repository.VideoStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private VideoStatsRepository videoStatsRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private CacheManager cacheManager;

    private VideoService videoService;

    @BeforeEach
    void setUp() {
        videoService = new VideoService(
                videoRepository,
                videoStatsRepository,
                channelRepository,
                idGenerator,
                kafkaTemplate,
                cacheManager);
    }

    @Test
    void shouldUploadVideo() {
        // Given
        VideoUploadRequest request = new VideoUploadRequest();
        request.setChannelId(1L);
        request.setTitle("Test Video");
        request.setDescription("Test description");
        request.setVisibility(Visibility.PUBLIC);

        User user = new User();
        user.setId(100L);
        user.setAccountStatus(AccountStatus.ACTIVE);

        Channel channel = new Channel();
        channel.setId(1L);
        channel.setOwner(user);
        channel.setDisplayName("Test Channel");

        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(idGenerator.nextId()).thenReturn(12345L);
        when(videoRepository.save(any(Video.class))).thenAnswer(inv -> inv.getArgument(0));
        when(videoStatsRepository.save(any(VideoStats.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        VideoResponse response = videoService.uploadVideo(request);

        // Then
        assertNotNull(response);
        assertEquals(12345L, response.getId());
        assertEquals("Test Video", response.getTitle());
        assertEquals(UploadStatus.PROCESSING, response.getStatus());

        verify(videoRepository).save(any(Video.class));
        verify(videoStatsRepository).save(any(VideoStats.class));
        verify(kafkaTemplate).send(eq("video-uploads"), eq("12345"), any());
    }

    @Test
    void shouldGetVideoById() {
        // Given
        Video video = new Video();
        video.setId(1L);
        video.setTitle("Test Video");
        video.setDescription("Description");
        video.setVisibility(Visibility.PUBLIC);
        video.setUploadStatus(UploadStatus.COMPLETED);
        video.setCreatedAt(LocalDateTime.now());

        User user = new User();
        user.setId(100L);

        Channel channel = new Channel();
        channel.setId(10L);
        channel.setHandle("@test");
        channel.setDisplayName("Test Channel");
        channel.setOwner(user);
        video.setChannel(channel);

        VideoStats stats = new VideoStats();
        stats.setVideoId(1L);
        stats.setViewCount(1000L);
        stats.setLikeCount(100L);
        stats.setDislikeCount(10L);
        stats.setCommentCount(50L);

        when(videoRepository.findByIdWithChannel(1L)).thenReturn(Optional.of(video));
        when(videoStatsRepository.findById(1L)).thenReturn(Optional.of(stats));

        // When
        VideoResponse response = videoService.getVideo(1L);

        // Then
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Test Video", response.getTitle());
        assertEquals(1000L, response.getViewCount());
        assertEquals(100L, response.getLikeCount());
    }

    @Test
    void shouldThrowWhenVideoNotFound() {
        when(videoRepository.findByIdWithChannel(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> videoService.getVideo(999L));
    }

    @Test
    void shouldThrowWhenChannelNotFoundOnUpload() {
        VideoUploadRequest request = new VideoUploadRequest();
        request.setChannelId(999L);
        request.setTitle("Test");

        when(channelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> videoService.uploadVideo(request));
    }

    @Test
    void shouldUpdateVideo() {
        // Given
        Video existingVideo = new Video();
        existingVideo.setId(1L);
        existingVideo.setTitle("Old Title");
        existingVideo.setDescription("Old Description");
        existingVideo.setVisibility(Visibility.PRIVATE);
        existingVideo.setUploadStatus(UploadStatus.COMPLETED);
        existingVideo.setCreatedAt(LocalDateTime.now());

        User user = new User();
        user.setId(100L);

        Channel channel = new Channel();
        channel.setId(10L);
        channel.setHandle("@test");
        channel.setDisplayName("Test Channel");
        channel.setOwner(user);
        existingVideo.setChannel(channel);

        VideoStats stats = new VideoStats();
        stats.setVideoId(1L);

        VideoUploadRequest updateRequest = new VideoUploadRequest();
        updateRequest.setTitle("New Title");
        updateRequest.setDescription("New Description");
        updateRequest.setVisibility(Visibility.PUBLIC);

        when(videoRepository.findById(1L)).thenReturn(Optional.of(existingVideo));
        when(videoRepository.save(any(Video.class))).thenAnswer(inv -> inv.getArgument(0));
        when(videoStatsRepository.findById(1L)).thenReturn(Optional.of(stats));

        // When
        VideoResponse response = videoService.updateVideo(1L, updateRequest);

        // Then
        assertNotNull(response);
        assertEquals("New Title", response.getTitle());
        assertEquals("New Description", response.getDescription());
        assertEquals(Visibility.PUBLIC, response.getVisibility());

        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(videoRepository).save(videoCaptor.capture());
        assertEquals("New Title", videoCaptor.getValue().getTitle());
    }
}
