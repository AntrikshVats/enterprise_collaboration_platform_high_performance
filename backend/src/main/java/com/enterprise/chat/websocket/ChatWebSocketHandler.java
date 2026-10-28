package com.enterprise.chat.websocket;

import com.enterprise.chat.dto.WebSocketMessage;
import com.enterprise.chat.entity.Message;
import com.enterprise.chat.security.JwtTokenProvider;
import com.enterprise.chat.service.PresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

/**
 * Core WebSocket handler for the chat platform.
 *
 * Architecture:
 * 1. Connection: Client connects with JWT token as query param (?token=...)
 * 2. Authentication: Token validated, userId extracted, session registered
 * 3. Message routing: Inbound JSON parsed, routed by type:
 *    - MESSAGE  → Published to Redis, queued for DB persistence
 *    - TYPING   → Published to Redis (ephemeral, not persisted)
 *    - SUBSCRIBE/UNSUBSCRIBE → Local channel subscription management
 *    - HEARTBEAT → Presence TTL refreshed in Redis
 * 4. Disconnect: Session cleaned up, presence set to OFFLINE
 *
 * Thread safety: Virtual threads handle each message independently.
 * Session sends are synchronized in SessionRegistry to prevent interleaving.
 */
@Component
@SuppressWarnings("null")
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final JwtTokenProvider jwtProvider;
    private final SessionRegistry sessionRegistry;
    private final MessageBatchWriter batchWriter;
    private final PresenceService presenceService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Session attribute keys
    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_USERNAME = "username";

    public ChatWebSocketHandler(JwtTokenProvider jwtProvider,
                                 SessionRegistry sessionRegistry,
                                 MessageBatchWriter batchWriter,
                                 PresenceService presenceService,
                                 RedisTemplate<String, String> redisTemplate,
                                 ObjectMapper objectMapper) {
        this.jwtProvider = jwtProvider;
        this.sessionRegistry = sessionRegistry;
        this.batchWriter = batchWriter;
        this.presenceService = presenceService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Extract JWT from query parameter
        String token = extractTokenFromUri(session);

        if (token == null || !jwtProvider.validateToken(token)) {
            log.warn("WebSocket connection rejected: invalid token");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        UUID userId = jwtProvider.getUserIdFromToken(token);
        String username = jwtProvider.getUsernameFromToken(token);

        // Store user info in session attributes
        session.getAttributes().put(ATTR_USER_ID, userId);
        session.getAttributes().put(ATTR_USERNAME, username);

        // Register in session registry
        sessionRegistry.register(userId, username, session);

        // Set presence to ONLINE
        presenceService.setOnline(userId, username);

        log.info("WebSocket connected: {} (active: {})",
                username, sessionRegistry.getActiveSessionCount());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage)
            throws Exception {
        UUID userId = getUserId(session);
        String username = getUsername(session);

        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        try {
            WebSocketMessage wsMessage = objectMapper.readValue(
                    textMessage.getPayload(), WebSocketMessage.class);

            switch (wsMessage.getType()) {
                case MESSAGE -> handleChatMessage(userId, username, wsMessage);
                case TYPING -> handleTyping(userId, username, wsMessage);
                case SUBSCRIBE -> handleSubscribe(userId, wsMessage);
                case UNSUBSCRIBE -> handleUnsubscribe(userId, wsMessage);
                case HEARTBEAT -> handleHeartbeat(userId, username);
                default -> {
                    String error = objectMapper.writeValueAsString(
                            WebSocketMessage.error(400, "Unknown message type"));
                    sessionRegistry.sendToUser(userId, error);
                }
            }
        } catch (Exception e) {
            log.error("Error handling message from {}: {}", username, e.getMessage());
            String error = objectMapper.writeValueAsString(
                    WebSocketMessage.error(500, "Internal server error"));
            sessionRegistry.sendToUser(userId, error);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
            throws Exception {
        UUID userId = getUserId(session);
        String username = getUsername(session);

        if (userId != null) {
            sessionRegistry.unregister(userId);
            presenceService.setOffline(userId, username);

            log.info("WebSocket disconnected: {} (status: {}, active: {})",
                    username, status, sessionRegistry.getActiveSessionCount());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
            throws Exception {
        UUID userId = getUserId(session);
        log.error("WebSocket transport error for user {}: {}",
                userId, exception.getMessage());

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // ============================================
    // Message Handlers
    // ============================================

    private void handleChatMessage(UUID senderId, String senderName,
                                    WebSocketMessage wsMessage) throws Exception {
        UUID channelId = UUID.fromString(wsMessage.getChannelId());
        UUID messageId = UUID.randomUUID();
        long timestamp = System.currentTimeMillis();

        // Create entity for DB persistence
        Message entity = new Message(channelId, senderId, wsMessage.getContent());
        entity.getKey().setId(messageId);

        // Enqueue for async batch write to DB
        batchWriter.enqueue(entity);

        // Create outbound message
        WebSocketMessage outbound = WebSocketMessage.chatMessage(
                messageId, channelId, senderId, senderName,
                wsMessage.getContent(), timestamp);

        String json = objectMapper.writeValueAsString(outbound);

        // Publish to Redis for cross-instance broadcast
        redisTemplate.convertAndSend("chat:channel:" + channelId, json);

        // Send ACK to sender
        String ack = objectMapper.writeValueAsString(WebSocketMessage.ack(messageId));
        sessionRegistry.sendToUser(senderId, ack);
    }

    private void handleTyping(UUID userId, String username,
                               WebSocketMessage wsMessage) throws Exception {
        UUID channelId = UUID.fromString(wsMessage.getChannelId());

        WebSocketMessage typing = WebSocketMessage.typing(channelId, userId, username);
        String json = objectMapper.writeValueAsString(typing);

        // Publish to Redis (ephemeral — not persisted)
        redisTemplate.convertAndSend("chat:typing:" + channelId, json);
    }

    private void handleSubscribe(UUID userId, WebSocketMessage wsMessage) {
        UUID channelId = UUID.fromString(wsMessage.getChannelId());
        sessionRegistry.subscribeToChannel(userId, channelId);
        log.debug("User {} subscribed to channel {}", userId, channelId);
    }

    private void handleUnsubscribe(UUID userId, WebSocketMessage wsMessage) {
        UUID channelId = UUID.fromString(wsMessage.getChannelId());
        sessionRegistry.unsubscribeFromChannel(userId, channelId);
        log.debug("User {} unsubscribed from channel {}", userId, channelId);
    }

    private void handleHeartbeat(UUID userId, String username) {
        presenceService.setOnline(userId, username);
    }

    // ============================================
    // Helpers
    // ============================================

    private String extractTokenFromUri(WebSocketSession session) {
        try {
            if (session.getUri() != null) {
                Map<String, String> params = UriComponentsBuilder
                        .fromUri(session.getUri())
                        .build()
                        .getQueryParams()
                        .toSingleValueMap();
                return params.get("token");
            }
        } catch (Exception e) {
            log.error("Error extracting token from URI", e);
        }
        return null;
    }

    private UUID getUserId(WebSocketSession session) {
        return (UUID) session.getAttributes().get(ATTR_USER_ID);
    }

    private String getUsername(WebSocketSession session) {
        return (String) session.getAttributes().get(ATTR_USERNAME);
    }
}
