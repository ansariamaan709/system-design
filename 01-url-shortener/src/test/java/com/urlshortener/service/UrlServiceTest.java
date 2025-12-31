package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private Base62Encoder encoder;

    @Mock
    private UrlCacheService cacheService;

    private UrlService urlService;

    @BeforeEach
    void setUp() {
        urlService = new UrlService(urlRepository, clickEventRepository, idGenerator,
                encoder, cacheService, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Should create short URL successfully")
    void shouldCreateShortUrl() {
        // Given
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://www.example.com/very/long/path");

        when(idGenerator.nextId()).thenReturn(123456789L);
        when(encoder.encode(123456789L)).thenReturn("abc123X");
        when(urlRepository.existsByShortCode("abc123X")).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UrlResponse response = urlService.createShortUrl(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getShortCode()).isEqualTo("abc123X");
        assertThat(response.getOriginalUrl()).isEqualTo("https://www.example.com/very/long/path");
        verify(urlRepository).save(any(Url.class));
        verify(cacheService).cacheUrl(any(Url.class));
    }

    @Test
    @DisplayName("Should create short URL with custom alias")
    void shouldCreateShortUrlWithCustomAlias() {
        // Given
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://www.example.com");
        request.setCustomAlias("mylink");

        when(urlRepository.existsByShortCode("mylink")).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UrlResponse response = urlService.createShortUrl(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getShortCode()).isEqualTo("mylink");
        assertThat(response.getIsCustomAlias()).isTrue();
    }

    @Test
    @DisplayName("Should throw exception when custom alias already exists")
    void shouldThrowExceptionWhenAliasExists() {
        // Given
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://www.example.com");
        request.setCustomAlias("existing");

        when(urlRepository.existsByShortCode("existing")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> urlService.createShortUrl(request))
                .isInstanceOf(AliasAlreadyExistsException.class)
                .hasMessageContaining("existing");
    }

    @Test
    @DisplayName("Should resolve URL from cache")
    void shouldResolveUrlFromCache() {
        // Given
        String shortCode = "abc123X";
        String originalUrl = "https://www.example.com";

        when(cacheService.getCachedUrl(shortCode)).thenReturn(originalUrl);

        // When
        String resolved = urlService.resolveUrl(shortCode);

        // Then
        assertThat(resolved).isEqualTo(originalUrl);
        verify(urlRepository, never()).findActiveByShortCode(anyString());
    }

    @Test
    @DisplayName("Should resolve URL from database when not in cache")
    void shouldResolveUrlFromDatabase() {
        // Given
        String shortCode = "abc123X";
        String originalUrl = "https://www.example.com";

        Url url = Url.builder()
                .shortCode(shortCode)
                .originalUrl(originalUrl)
                .isActive(true)
                .build();

        when(cacheService.getCachedUrl(shortCode)).thenReturn(null);
        when(urlRepository.findActiveByShortCode(shortCode)).thenReturn(Optional.of(url));

        // When
        String resolved = urlService.resolveUrl(shortCode);

        // Then
        assertThat(resolved).isEqualTo(originalUrl);
        verify(cacheService).cacheUrl(any(Url.class));
    }

    @Test
    @DisplayName("Should throw exception when URL not found")
    void shouldThrowExceptionWhenUrlNotFound() {
        // Given
        String shortCode = "notfound";

        when(cacheService.getCachedUrl(shortCode)).thenReturn(null);
        when(urlRepository.findActiveByShortCode(shortCode)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> urlService.resolveUrl(shortCode))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    @DisplayName("Should create URL with expiration")
    void shouldCreateUrlWithExpiration() {
        // Given
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://www.example.com");
        request.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(idGenerator.nextId()).thenReturn(123456789L);
        when(encoder.encode(123456789L)).thenReturn("abc123X");
        when(urlRepository.existsByShortCode("abc123X")).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        UrlResponse response = urlService.createShortUrl(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getExpiresAt()).isNotNull();
    }
}
