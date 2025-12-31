package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.GlobalExceptionHandler;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.service.ClickEventService;
import com.urlshortener.service.UrlService;
import com.urlshortener.service.UserAgentParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UrlControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UrlService urlService;

    @Mock
    private ClickEventService clickEventService;

    @Mock
    private UserAgentParser userAgentParser;

    @InjectMocks
    private UrlController urlController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(urlController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    @DisplayName("Should create short URL successfully")
    void shouldCreateShortUrl() throws Exception {
        // Given
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://www.example.com/very/long/path");

        UrlResponse response = UrlResponse.builder()
                .shortCode("abc123X")
                .shortUrl("http://localhost:8080/abc123X")
                .originalUrl("https://www.example.com/very/long/path")
                .clickCount(0L)
                .isActive(true)
                .isCustomAlias(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(urlService.createShortUrl(any(CreateUrlRequest.class)))
                .thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shortCode").value("abc123X"))
                .andExpect(jsonPath("$.data.originalUrl").value("https://www.example.com/very/long/path"));
    }

    @Test
    @DisplayName("Should return 400 for invalid URL")
    void shouldReturn400ForInvalidUrl() throws Exception {
        // Given
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("not-a-valid-url");

        // When/Then
        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should redirect to original URL")
    void shouldRedirectToOriginalUrl() throws Exception {
        // Given
        String shortCode = "abc123X";
        String originalUrl = "https://www.example.com";

        when(urlService.resolveUrl(shortCode)).thenReturn(originalUrl);

        // When/Then
        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", originalUrl));
    }

    @Test
    @DisplayName("Should return 404 for non-existent short code")
    void shouldReturn404ForNonExistentShortCode() throws Exception {
        // Given
        String shortCode = "notfound";

        when(urlService.resolveUrl(shortCode))
                .thenThrow(new UrlNotFoundException("URL not found: " + shortCode));

        // When/Then
        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should return 409 when alias already exists")
    void shouldReturn409WhenAliasExists() throws Exception {
        // Given
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://www.example.com");
        request.setCustomAlias("existing");

        when(urlService.createShortUrl(any(CreateUrlRequest.class)))
                .thenThrow(new AliasAlreadyExistsException("Alias already exists: existing"));

        // When/Then
        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should get URL info")
    void shouldGetUrlInfo() throws Exception {
        // Given
        String shortCode = "abc123X";
        UrlResponse response = UrlResponse.builder()
                .shortCode(shortCode)
                .shortUrl("http://localhost:8080/" + shortCode)
                .originalUrl("https://www.example.com")
                .clickCount(100L)
                .isActive(true)
                .build();

        when(urlService.getUrlInfo(shortCode)).thenReturn(response);

        // When/Then
        mockMvc.perform(get("/api/v1/urls/{shortCode}", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shortCode").value(shortCode))
                .andExpect(jsonPath("$.data.clickCount").value(100));
    }

    @Test
    @DisplayName("Should get URL stats")
    void shouldGetUrlStats() throws Exception {
        // Given
        String shortCode = "abc123X";
        UrlStatsResponse stats = UrlStatsResponse.builder()
                .shortCode(shortCode)
                .originalUrl("https://www.example.com")
                .totalClicks(100L)
                .clicksByCountry(Collections.singletonMap("US", 50L))
                .clicksByBrowser(Collections.singletonMap("Chrome", 60L))
                .clicksByDevice(Collections.singletonMap("Desktop", 70L))
                .build();

        when(urlService.getUrlStats(shortCode)).thenReturn(stats);

        // When/Then
        mockMvc.perform(get("/api/v1/urls/{shortCode}/stats", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalClicks").value(100))
                .andExpect(jsonPath("$.data.clicksByCountry.US").value(50));
    }

    @Test
    @DisplayName("Should delete URL")
    void shouldDeleteUrl() throws Exception {
        // Given
        String shortCode = "abc123X";
        doNothing().when(urlService).deleteUrl(shortCode, null);

        // When/Then
        mockMvc.perform(delete("/api/v1/urls/{shortCode}", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(urlService).deleteUrl(shortCode, null);
    }
}
