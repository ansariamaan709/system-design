package com.urlshortener.service;

import com.urlshortener.dto.ClickEventDto;
import com.urlshortener.entity.ClickEvent;
import com.urlshortener.repository.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClickEventService {

    private final ClickEventRepository clickEventRepository;
    private final UrlService urlService;

    @Async
    public void recordClickEvent(ClickEventDto dto) {
        try {
            // Create and save click event
            ClickEvent event = ClickEvent.builder()
                    .shortCode(dto.getShortCode())
                    .ipAddress(dto.getIpAddress())
                    .userAgent(dto.getUserAgent())
                    .referrer(dto.getReferrer())
                    .country(dto.getCountry())
                    .city(dto.getCity())
                    .deviceType(dto.getDeviceType())
                    .browser(dto.getBrowser())
                    .os(dto.getOs())
                    .build();

            clickEventRepository.save(event);

            // Increment click count
            urlService.incrementClickCount(dto.getShortCode());

            log.debug("Recorded click event for: {}", dto.getShortCode());
        } catch (Exception e) {
            log.error("Failed to record click event for: {}", dto.getShortCode(), e);
        }
    }
}
