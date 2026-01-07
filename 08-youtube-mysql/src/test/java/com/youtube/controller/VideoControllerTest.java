package com.youtube.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youtube.dto.VideoResponse;
import com.youtube.dto.VideoUploadRequest;
import com.youtube.entity.enums.UploadStatus;
import com.youtube.entity.enums.Visibility;
import com.youtube.service.VideoService;
import com.youtube.service.ViewCountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VideoController.class)
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VideoService videoService;

    @MockBean
    private ViewCountService viewCountService;

    @Test
    void shouldUploadVideo() throws Exception {
        VideoUploadRequest request = new VideoUploadRequest();
        request.setChannelId(1L);
        request.setTitle("Test Video");
        request.setDescription("Description");
        request.setVisibility(Visibility.PUBLIC);

        VideoResponse response = VideoResponse.builder()
                .id(1L)
                .title("Test Video")
                .description("Description")
                .visibility(Visibility.PUBLIC)
                .status(UploadStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();

        when(videoService.uploadVideo(any(VideoUploadRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/videos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Test Video"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void shouldGetVideo() throws Exception {
        VideoResponse response = VideoResponse.builder()
                .id(1L)
                .title("Test Video")
                .description("Description")
                .visibility(Visibility.PUBLIC)
                .status(UploadStatus.COMPLETED)
                .viewCount(1000L)
                .likeCount(100L)
                .createdAt(LocalDateTime.now())
                .build();

        when(videoService.getVideo(1L)).thenReturn(response);

        mockMvc.perform(get("/api/videos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Test Video"))
                .andExpect(jsonPath("$.viewCount").value(1000));
    }

    @Test
    void shouldRecordView() throws Exception {
        doNothing().when(viewCountService).recordView(anyLong(), anyLong(), anyString(), anyString());

        mockMvc.perform(post("/api/videos/1/view")
                .param("userId", "100"))
                .andExpect(status().isOk());

        verify(viewCountService).recordView(eq(1L), eq(100L), anyString(), anyString());
    }

    @Test
    void shouldUpdateVideo() throws Exception {
        VideoUploadRequest request = new VideoUploadRequest();
        request.setTitle("Updated Title");
        request.setDescription("Updated Description");

        VideoResponse response = VideoResponse.builder()
                .id(1L)
                .title("Updated Title")
                .description("Updated Description")
                .visibility(Visibility.PUBLIC)
                .status(UploadStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        when(videoService.updateVideo(eq(1L), any(VideoUploadRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/videos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    void shouldDeleteVideo() throws Exception {
        doNothing().when(videoService).deleteVideo(1L);

        mockMvc.perform(delete("/api/videos/1"))
                .andExpect(status().isNoContent());

        verify(videoService).deleteVideo(1L);
    }
}
