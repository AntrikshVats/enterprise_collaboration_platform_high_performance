package com.enterprise.chat.config;

import com.enterprise.chat.websocket.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * WebSocket configuration for raw WebSocket (no STOMP).
 *
 * Key decisions:
 * - Raw WebSocket for minimum per-connection memory overhead
 * - JWT auth via query parameter (?token=...) validated in ChatWebSocketHandler
 * - Allowed origins set to * for development; restrict in production
 */
@Configuration
@EnableWebSocket
@SuppressWarnings("null")
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(@org.springframework.lang.NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .setAllowedOrigins("*")
                .setHandshakeHandler(new DefaultHandshakeHandler());
    }
}
