package com.urlshortener.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${url-shortener.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${url-shortener.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Skip rate limiting for health checks and actuator endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/health") || path.startsWith("/actuator") ||
                path.startsWith("/swagger") || path.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientIp, this::createNewBucket);

        if (bucket.tryConsume(1)) {
            // Add rate limit headers
            response.addHeader("X-Rate-Limit-Remaining",
                    String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Rate limit exceeded. Please try again later.\",\"data\":null}");
        }
    }

    @SuppressWarnings("deprecation")
    private Bucket createNewBucket(String key) {
        Bandwidth limit = Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    // Cleanup old buckets periodically (called by scheduled task)
    public void cleanupBuckets() {
        // In production, implement TTL-based cleanup or use Redis-based buckets
        if (buckets.size() > 10000) {
            buckets.clear();
        }
    }
}
