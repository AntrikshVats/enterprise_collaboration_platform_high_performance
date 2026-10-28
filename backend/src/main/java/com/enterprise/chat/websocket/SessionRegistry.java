package com.enterprise.chat.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry mapping users to WebSocket sessions and
 * channels to subscribed user sessions.
 *
 * Design decisions:
 * - ConcurrentHashMap for O(1) lookup and lock-free reads
 * - One session per user (last-write-wins for multi-device; can extend later)
 * - Channel subscriptions tracked bidirectionally for fast cleanup on disconnect
 *
 * Memory per entry: ~200 bytes (UUID key + session ref + subscription set)
 * At 50k users: ~10 MB total — negligible
 */
@Component
@SuppressWarnings("null")
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    // userId -> WebSocketSession
    private final Map<UUID, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    // channelId -> Set<userId> (subscribers)
    private final Map<UUID, Set<UUID>> channelSubscriptions = new ConcurrentHashMap<>();

    // userId -> Set<channelId> (reverse index for cleanup on disconnect)
    private final Map<UUID, Set<UUID>> userChannels = new ConcurrentHashMap<>();

    // userId -> username (cached for fast lookup in broadcasts)
    private final Map<UUID, String> usernames = new ConcurrentHashMap<>();

    public void register(UUID userId, String username, WebSocketSession session) {
        // Close previous session if exists (multi-device last-write-wins)
        WebSocketSession existing = userSessions.put(userId, session);
        usernames.put(userId, username);

        if (existing != null && existing.isOpen()) {
            try {
                existing.close();
            } catch (IOException e) {
                log.warn("Error closing existing session for user {}", userId, e);
            }
        }

        log.info("Registered session for user {} (total: {})", username, userSessions.size());
    }

    public void unregister(UUID userId) {
        userSessions.remove(userId);
        usernames.remove(userId);

        // Clean up all channel subscriptions for this user
        Set<UUID> channels = userChannels.remove(userId);
        if (channels != null) {
            for (UUID channelId : channels) {
                Set<UUID> subscribers = channelSubscriptions.get(channelId);
                if (subscribers != null) {
                    subscribers.remove(userId);
                    if (subscribers.isEmpty()) {
                        channelSubscriptions.remove(channelId);
                    }
                }
            }
        }

        log.debug("Unregistered session for user {} (total: {})", userId, userSessions.size());
    }

    public void subscribeToChannel(UUID userId, UUID channelId) {
        channelSubscriptions
                .computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet())
                .add(userId);
        userChannels
                .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(channelId);
    }

    public void unsubscribeFromChannel(UUID userId, UUID channelId) {
        Set<UUID> subscribers = channelSubscriptions.get(channelId);
        if (subscribers != null) {
            subscribers.remove(userId);
        }
        Set<UUID> channels = userChannels.get(userId);
        if (channels != null) {
            channels.remove(channelId);
        }
    }

    /**
     * Broadcast a message to all users subscribed to a channel.
     * Non-blocking: failures on individual sessions are logged and cleaned up.
     */
    public void broadcastToChannel(UUID channelId, String jsonMessage) {
        Set<UUID> subscribers = channelSubscriptions.get(channelId);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        TextMessage textMessage = new TextMessage(jsonMessage);

        for (UUID userId : subscribers) {
            WebSocketSession session = userSessions.get(userId);
            if (session != null && session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.warn("Failed to send message to user {}, removing session", userId);
                    unregister(userId);
                }
            }
        }
    }

    /**
     * Send a message to a specific user.
     */
    public void sendToUser(UUID userId, String jsonMessage) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            } catch (IOException e) {
                log.warn("Failed to send to user {}", userId, e);
                unregister(userId);
            }
        }
    }

    /**
     * Broadcast a message to ALL connected users (for global events like presence).
     */
    public void broadcastToAll(String jsonMessage) {
        TextMessage textMessage = new TextMessage(jsonMessage);

        for (Map.Entry<UUID, WebSocketSession> entry : userSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.warn("Failed to broadcast to user {}", entry.getKey());
                    unregister(entry.getKey());
                }
            }
        }
    }

    public WebSocketSession getSession(UUID userId) {
        return userSessions.get(userId);
    }

    public String getUsername(UUID userId) {
        return usernames.get(userId);
    }

    public Set<UUID> getSubscribers(UUID channelId) {
        return channelSubscriptions.getOrDefault(channelId, Collections.emptySet());
    }

    public int getActiveSessionCount() {
        return userSessions.size();
    }

    public Set<UUID> getOnlineUserIds() {
        return Collections.unmodifiableSet(userSessions.keySet());
    }
}
