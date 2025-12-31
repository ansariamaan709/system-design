package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEventDto {

    private String shortCode;
    private String ipAddress;
    private String userAgent;
    private String referrer;
    private String country;
    private String city;
    private String deviceType;
    private String browser;
    private String os;
}
