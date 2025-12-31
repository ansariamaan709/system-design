package com.stockexchange.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockexchange.dto.MarketDataMessage;
import com.stockexchange.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time market data streaming.
 * 
 * Supports:
 * - Quote subscriptions (Level 1)
 * - Depth subscriptions (Level 2)
 * - Trade subscriptions
 * - Ticker subscriptions
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private final MarketDataService marketDataService;
    private final ObjectMapper objectMapper;

    // Session management
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> symbolSubscribers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionSubscriptions.put(sessionId, ConcurrentHashMap.newKeySet());

        log.info("[WS] Connection established: {}", sessionId);

        // Send welcome message
        sendMessage(session, MarketDataMessage.builder()
                .type(MarketDataMessage.MessageType.SYSTEM_STATUS)
                .timestamp(System.currentTimeMillis())
                .data(Map.of("status", "connected", "sessionId", sessionId))
                .build());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();

        // Remove all subscriptions
        Set<String> subs = sessionSubscriptions.remove(sessionId);
        if (subs != null) {
            for (String sub : subs) {
                Set<String> subscribers = symbolSubscribers.get(sub);
                if (subscribers != null) {
                    subscribers.remove(sessionId);
                }
            }
        }

        sessions.remove(sessionId);
        log.info("[WS] Connection closed: {} - {}", sessionId, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            MarketDataMessage msg = objectMapper.readValue(message.getPayload(), MarketDataMessage.class);

            switch (msg.getType()) {
                case SUBSCRIBE -> handleSubscribe(session, msg);
                case UNSUBSCRIBE -> handleUnsubscribe(session, msg);
                default -> log.warn("[WS] Unknown message type: {}", msg.getType());
            }
        } catch (Exception e) {
            log.error("[WS] Error handling message: {}", e.getMessage());
            sendError(session, "Invalid message format: " + e.getMessage());
        }
    }

    private void handleSubscribe(WebSocketSession session, MarketDataMessage msg) {
        String symbol = msg.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            sendError(session, "Symbol is required");
            return;
        }

        symbol = symbol.toUpperCase();
        String sessionId = session.getId();

        // Add to subscriptions
        sessionSubscriptions.get(sessionId).add(symbol);
        symbolSubscribers.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        log.info("[WS] {} subscribed to {}", sessionId, symbol);

        // Send confirmation
        sendMessage(session, MarketDataMessage.builder()
                .type(MarketDataMessage.MessageType.SUBSCRIBED)
                .symbol(symbol)
                .timestamp(System.currentTimeMillis())
                .build());

        // Send current snapshot
        sendSnapshot(session, symbol);
    }

    private void handleUnsubscribe(WebSocketSession session, MarketDataMessage msg) {
        String symbol = msg.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            sendError(session, "Symbol is required");
            return;
        }

        symbol = symbol.toUpperCase();
        String sessionId = session.getId();

        // Remove from subscriptions
        sessionSubscriptions.get(sessionId).remove(symbol);
        Set<String> subscribers = symbolSubscribers.get(symbol);
        if (subscribers != null) {
            subscribers.remove(sessionId);
        }

        log.info("[WS] {} unsubscribed from {}", sessionId, symbol);

        // Send confirmation
        sendMessage(session, MarketDataMessage.builder()
                .type(MarketDataMessage.MessageType.UNSUBSCRIBED)
                .symbol(symbol)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private void sendSnapshot(WebSocketSession session, String symbol) {
        var response = marketDataService.getDepth(symbol, 10);
        if (response != null) {
            sendMessage(session, MarketDataMessage.builder()
                    .type(MarketDataMessage.MessageType.SNAPSHOT)
                    .symbol(symbol)
                    .timestamp(System.currentTimeMillis())
                    .data(response)
                    .build());
        }
    }

    // ==================== Broadcasting Methods ====================

    /**
     * Broadcast quote update to all subscribers.
     */
    public void broadcastQuote(String symbol, MarketDataMessage.Quote quote) {
        broadcast(symbol, MarketDataMessage.quote(quote));
    }

    /**
     * Broadcast trade to all subscribers.
     */
    public void broadcastTrade(String symbol, MarketDataMessage.TradeTick trade) {
        broadcast(symbol, MarketDataMessage.trade(trade));
    }

    /**
     * Broadcast depth update to all subscribers.
     */
    public void broadcastDepth(String symbol, MarketDataMessage.DepthUpdate depth) {
        broadcast(symbol, MarketDataMessage.depth(depth));
    }

    /**
     * Broadcast ticker to all subscribers.
     */
    public void broadcastTicker(String symbol, MarketDataMessage.Ticker ticker) {
        broadcast(symbol, MarketDataMessage.ticker(ticker));
    }

    /**
     * Broadcast to all connected sessions.
     */
    public void broadcastToAll(MarketDataMessage message) {
        for (WebSocketSession session : sessions.values()) {
            sendMessage(session, message);
        }
    }

    private void broadcast(String symbol, MarketDataMessage message) {
        Set<String> subscribers = symbolSubscribers.get(symbol);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        for (String sessionId : subscribers) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                sendMessage(session, message);
            }
        }
    }

    private void sendMessage(WebSocketSession session, MarketDataMessage message) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("[WS] Error sending message: {}", e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String error) {
        sendMessage(session, MarketDataMessage.error(error));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[WS] Transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    // ==================== Statistics ====================

    public int getConnectionCount() {
        return sessions.size();
    }

    public int getSubscriptionCount(String symbol) {
        Set<String> subscribers = symbolSubscribers.get(symbol);
        return subscribers != null ? subscribers.size() : 0;
    }

    public Map<String, Integer> getSubscriptionCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : symbolSubscribers.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }
}
