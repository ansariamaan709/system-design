package com.uber.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.entity.DriverLocation;
import com.uber.service.LocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * WebSocket handler for real-time location streaming.
 * 
 * Supports:
 * - Driver location tracking during active rides
 * - Nearby drivers streaming for map display
 * 
 * Message types:
 * - SUBSCRIBE_DRIVER: Subscribe to a specific driver's location
 * - UNSUBSCRIBE_DRIVER: Unsubscribe from driver location
 * - SUBSCRIBE_NEARBY: Subscribe to nearby drivers updates
 * - PING: Keep-alive message
 */
@Component
@Slf4j
public class LocationWebSocketHandler extends TextWebSocketHandler {
    
    private final LocationService locationService;
    private final ObjectMapper objectMapper;
    
    // Track active subscriptions
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, String> driverSubscriptions = new ConcurrentHashMap<>(); // sessionId -> driverId
    
    // Scheduled executor for periodic updates
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    
    public LocationWebSocketHandler(LocationService locationService, ObjectMapper objectMapper) {
        this.locationService = locationService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionMap.put(session.getId(), session);
        log.info("WebSocket connection established: {}", session.getId());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");
            
            switch (type) {
                case "SUBSCRIBE_DRIVER" -> handleSubscribeDriver(session, payload);
                case "UNSUBSCRIBE_DRIVER" -> handleUnsubscribeDriver(session);
                case "SUBSCRIBE_NEARBY" -> handleSubscribeNearby(session, payload);
                case "PING" -> handlePing(session);
                default -> sendError(session, "Unknown message type: " + type);
            }
            
        } catch (Exception e) {
            log.error("Error handling WebSocket message: {}", e.getMessage());
            sendError(session, "Invalid message format");
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        cleanup(session);
        log.info("WebSocket connection closed: {} ({})", session.getId(), status);
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        cleanup(session);
    }
    
    /**
     * Subscribe to a specific driver's location updates.
     * Used when tracking a driver during an active ride.
     */
    private void handleSubscribeDriver(WebSocketSession session, Map<String, Object> payload) {
        String driverId = (String) payload.get("driverId");
        if (driverId == null) {
            sendError(session, "driverId is required");
            return;
        }
        
        // Cancel any existing subscription
        handleUnsubscribeDriver(session);
        
        driverSubscriptions.put(session.getId(), driverId);
        
        // Schedule periodic location updates
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendDriverLocation(session, UUID.fromString(driverId));
            } catch (Exception e) {
                log.error("Error sending driver location: {}", e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);
        
        scheduledTasks.put(session.getId(), task);
        
        log.debug("Session {} subscribed to driver {}", session.getId(), driverId);
        
        // Send confirmation
        sendMessage(session, Map.of(
                "type", "SUBSCRIBED",
                "driverId", driverId
        ));
    }
    
    /**
     * Unsubscribe from driver location updates.
     */
    private void handleUnsubscribeDriver(WebSocketSession session) {
        String sessionId = session.getId();
        String driverId = driverSubscriptions.remove(sessionId);
        
        ScheduledFuture<?> task = scheduledTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
        
        if (driverId != null) {
            log.debug("Session {} unsubscribed from driver {}", sessionId, driverId);
        }
    }
    
    /**
     * Subscribe to nearby drivers updates.
     * Used for displaying drivers on the map.
     */
    private void handleSubscribeNearby(WebSocketSession session, Map<String, Object> payload) {
        double lat = ((Number) payload.get("lat")).doubleValue();
        double lng = ((Number) payload.get("lng")).doubleValue();
        int radius = payload.containsKey("radius") ? ((Number) payload.get("radius")).intValue() : 5000;
        
        // For now, just acknowledge - full implementation would track area subscriptions
        sendMessage(session, Map.of(
                "type", "SUBSCRIBED_NEARBY",
                "lat", lat,
                "lng", lng,
                "radius", radius
        ));
        
        log.debug("Session {} subscribed to nearby at ({}, {}) r={}", session.getId(), lat, lng, radius);
    }
    
    /**
     * Handle keep-alive ping.
     */
    private void handlePing(WebSocketSession session) {
        sendMessage(session, Map.of("type", "PONG", "timestamp", System.currentTimeMillis()));
    }
    
    /**
     * Send driver location update to subscribed session.
     */
    private void sendDriverLocation(WebSocketSession session, UUID driverId) {
        if (!session.isOpen()) {
            cleanup(session);
            return;
        }
        
        DriverLocation location = locationService.getDriverLocation(driverId);
        
        if (location != null) {
            sendMessage(session, Map.of(
                    "type", "LOCATION_UPDATE",
                    "driverId", driverId.toString(),
                    "latitude", location.getLatitude(),
                    "longitude", location.getLongitude(),
                    "heading", location.getHeading() != null ? location.getHeading() : 0,
                    "timestamp", location.getUpdatedAt().toEpochMilli()
            ));
        }
    }
    
    /**
     * Send message to WebSocket session.
     */
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        if (!session.isOpen()) {
            return;
        }
        
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send WebSocket message: {}", e.getMessage());
        }
    }
    
    /**
     * Send error message to session.
     */
    private void sendError(WebSocketSession session, String error) {
        sendMessage(session, Map.of(
                "type", "ERROR",
                "message", error,
                "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * Clean up session resources.
     */
    private void cleanup(WebSocketSession session) {
        String sessionId = session.getId();
        sessionMap.remove(sessionId);
        driverSubscriptions.remove(sessionId);
        
        ScheduledFuture<?> task = scheduledTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
    }
    
    /**
     * Broadcast location update to all subscribers of a driver.
     * Called by LocationService when a driver location is updated.
     */
    public void broadcastDriverLocation(UUID driverId, DriverLocation location) {
        String driverIdStr = driverId.toString();
        
        driverSubscriptions.forEach((sessionId, subscribedDriverId) -> {
            if (subscribedDriverId.equals(driverIdStr)) {
                WebSocketSession session = sessionMap.get(sessionId);
                if (session != null && session.isOpen()) {
                    sendMessage(session, Map.of(
                            "type", "LOCATION_UPDATE",
                            "driverId", driverIdStr,
                            "latitude", location.getLatitude(),
                            "longitude", location.getLongitude(),
                            "heading", location.getHeading() != null ? location.getHeading() : 0,
                            "timestamp", location.getUpdatedAt().toEpochMilli()
                    ));
                }
            }
        });
    }
}
