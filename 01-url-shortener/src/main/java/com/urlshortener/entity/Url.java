package com.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "urls", indexes = {
        @Index(name = "idx_short_code", columnList = "shortCode", unique = true),
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_expires_at", columnList = "expiresAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Url {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String shortCode;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(length = 64)
    private String userId;

    @Column(nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Custom alias flag
    @Column(nullable = false)
    @Builder.Default
    private Boolean isCustomAlias = false;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public void incrementClickCount() {
        this.clickCount++;
    }
}
