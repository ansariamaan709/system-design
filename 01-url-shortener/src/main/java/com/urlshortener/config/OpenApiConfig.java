package com.urlshortener.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${url-shortener.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(new Info()
                        .title("URL Shortener API")
                        .description("""
                                Production-grade URL Shortening Service API

                                ## Features
                                - Create short URLs from long URLs
                                - Custom aliases support
                                - URL expiration
                                - Click analytics and statistics
                                - Rate limiting protection

                                ## Rate Limits
                                - 60 requests per minute per IP address

                                ## Short Code Format
                                - 7 characters using Base62 encoding (0-9, A-Z, a-z)
                                - Example: `abc123X`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@urlshortener.com")
                                .url("https://github.com/urlshortener"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url(baseUrl)
                                .description("Current Server"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development"),
                        new Server()
                                .url("https://api.short.url")
                                .description("Production Server")));
    }
}
