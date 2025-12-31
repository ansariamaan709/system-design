package com.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "click_events", indexes = {
        @Index(name = "idx_click_short_code", columnList = "shortCode"),
        @Index(name = "idx_click_timestamp", columnList = "clickedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String shortCode;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @Column(length = 512)
    private String referrer;

    @Column(length = 2)
    private String country;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String deviceType;

    @Column(length = 50)
    private String browser;

    @Column(length = 50)
    private String os;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime clickedAt;
}
