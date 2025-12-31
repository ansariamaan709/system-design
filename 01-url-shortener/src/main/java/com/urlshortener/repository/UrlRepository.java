package com.urlshortener.repository;

import com.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    Optional<Url> findByOriginalUrlAndUserId(String originalUrl, String userId);

    Optional<Url> findByOriginalUrlAndUserIdIsNull(String originalUrl);

    List<Url> findByUserId(String userId);

    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :shortCode")
    void incrementClickCount(@Param("shortCode") String shortCode);

    @Modifying
    @Query("UPDATE Url u SET u.isActive = false WHERE u.expiresAt < :now AND u.isActive = true")
    int deactivateExpiredUrls(@Param("now") LocalDateTime now);

    @Query("SELECT u FROM Url u WHERE u.shortCode = :shortCode AND u.isActive = true")
    Optional<Url> findActiveByShortCode(@Param("shortCode") String shortCode);

    @Query("SELECT COUNT(u) FROM Url u WHERE u.createdAt >= :since")
    long countUrlsCreatedSince(@Param("since") LocalDateTime since);
}
