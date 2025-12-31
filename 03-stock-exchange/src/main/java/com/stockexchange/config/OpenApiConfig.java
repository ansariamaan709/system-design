package com.stockexchange.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger documentation configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockExchangeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stock Exchange API")
                        .description("""
                                High-performance Stock Exchange Trading API.

                                ## Features
                                - Order Management (New, Cancel, Modify)
                                - Real-time Market Data (Level 1 & Level 2)
                                - Account & Portfolio Management
                                - Trade History

                                ## WebSocket Endpoints
                                - `/ws/market-data` - Real-time market data (raw WebSocket)
                                - `/ws/stomp` - STOMP over WebSocket

                                ## Authentication
                                Include `X-Client-Id` and `X-Account-Id` headers with each request.
                                For WebSocket, authenticate via the initial connection message.

                                ## Rate Limits
                                - REST API: 100 requests/second per client
                                - WebSocket: 1000 messages/second per connection
                                - Orders: Configurable per account
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Exchange Support")
                                .email("support@stockexchange.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://stockexchange.com/terms")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://api.stockexchange.com").description("Production")))
                .components(new Components()
                        .addSecuritySchemes("ClientId", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Client-Id")
                                .description("Client identifier"))
                        .addSecuritySchemes("AccountId", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Account-Id")
                                .description("Account identifier")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("ClientId")
                        .addList("AccountId"));
    }
}
