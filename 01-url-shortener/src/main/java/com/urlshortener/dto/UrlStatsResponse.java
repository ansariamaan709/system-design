package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlStatsResponse {

    private String shortCode;
    private String originalUrl;
    private Long totalClicks;
    private Long clicksLast24Hours;
    private Long clicksLast7Days;
    private Long clicksLast30Days;

    private Map<String, Long> clicksByCountry;
    private Map<String, Long> clicksByBrowser;
    private Map<String, Long> clicksByDevice;
    private List<DailyClickStat> dailyClicks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyClickStat {
        private LocalDate date;
        private Long clicks;
    }
}
