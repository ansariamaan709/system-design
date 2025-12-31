package com.urlshortener.repository;

import com.urlshortener.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    List<ClickEvent> findByShortCode(String shortCode);

    long countByShortCode(String shortCode);

    @Query("SELECT COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode AND c.clickedAt >= :since")
    long countByShortCodeSince(@Param("shortCode") String shortCode, @Param("since") LocalDateTime since);

    @Query("SELECT c.country, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.country ORDER BY COUNT(c) DESC")
    List<Object[]> getCountryStats(@Param("shortCode") String shortCode);

    @Query("SELECT c.browser, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.browser ORDER BY COUNT(c) DESC")
    List<Object[]> getBrowserStats(@Param("shortCode") String shortCode);

    @Query("SELECT c.deviceType, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.deviceType ORDER BY COUNT(c) DESC")
    List<Object[]> getDeviceStats(@Param("shortCode") String shortCode);

    @Query("SELECT FUNCTION('DATE', c.clickedAt), COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode AND c.clickedAt >= :since GROUP BY FUNCTION('DATE', c.clickedAt) ORDER BY FUNCTION('DATE', c.clickedAt)")
    List<Object[]> getDailyClickStats(@Param("shortCode") String shortCode, @Param("since") LocalDateTime since);
}
