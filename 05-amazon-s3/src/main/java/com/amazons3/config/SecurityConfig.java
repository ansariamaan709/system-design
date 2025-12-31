package com.amazons3.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for S3 API.
 * In production, this would implement AWS Signature Version 4 authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${security.enabled:false}")
    private boolean securityEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (securityEnabled) {
            // In production, add AWS Signature V4 authentication filter
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().authenticated());
        } else {
            // Development mode - allow all requests
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }
}
