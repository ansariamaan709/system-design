package com.stockexchange.config;

import com.stockexchange.websocket.MarketDataWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket configuration for real-time market data.
 */
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer, WebSocketMessageBrokerConfigurer {

    private final MarketDataWebSocketHandler marketDataWebSocketHandler;

    // ==================== Raw WebSocket Configuration ====================

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Raw WebSocket endpoint for low-latency connections
        registry.addHandler(marketDataWebSocketHandler, "/ws/market-data")
                .setAllowedOrigins("*")
                .withSockJS(); // Fallback for browsers without WebSocket support
    }

    // ==================== STOMP over WebSocket Configuration ====================

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for broadcasting
        config.enableSimpleBroker("/topic", "/queue");

        // Prefix for client-to-server messages
        config.setApplicationDestinationPrefixes("/app");

        // User destination prefix for private messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // STOMP endpoint for higher-level messaging
        registry.addEndpoint("/ws/stomp")
                .setAllowedOrigins("*")
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Configure transport settings for performance
        registration
                .setMessageSizeLimit(64 * 1024) // 64KB max message size
                .setSendBufferSizeLimit(512 * 1024) // 512KB send buffer
                .setSendTimeLimit(20000); // 20 seconds send timeout
    }
}
