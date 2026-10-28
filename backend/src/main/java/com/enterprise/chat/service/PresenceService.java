package com.enterprise.chat.service;

import com.enterprise.chat.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Presence service using Redis as the source of truth.
 *
 * Design:
 * - Each online user has a Redis key "presence:{userId}" with TTL
 * - WebSocket heartbeat refreshes the TTL every 30s (client-side)
 * - If heartbeat stops, TTL expires and user is considered offline
 * - Presence changes are published to Redis "chat:presence" channel
 *   for real-time broadcast to all connected clients
 */
@Service
@SuppressWarnings("null")
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final String PRESENCE_KEY_PREFIX = "presence:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.presence.ttl-seconds:60}")
    private long ttlSeconds;

    public PresenceService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void setOnline(UUID userId, String username) {
        String key = PRESENCE_KEY_PREFIX + userId;
        Boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(
                key, username, ttlSeconds, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(wasAbsent)) {
            // User just came online — broadcast presence change
            publishPresenceChange(userId, username, "ONLINE");
        } else {
            // User already online — just refresh TTL (heartbeat)
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    public void setOffline(UUID userId, String username) {
        String key = PRESENCE_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        publishPresenceChange(userId, username, "OFFLINE");
    }

    public boolean isOnline(UUID userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Set<String> getOnlineUserIds() {
        Set<String> keys = redisTemplate.keys(PRESENCE_KEY_PREFIX + "*");
        Set<String> userIds = new HashSet<>();
        if (keys != null) {
            for (String key : keys) {
                userIds.add(key.replace(PRESENCE_KEY_PREFIX, ""));
            }
        }
        return userIds;
    }

    private void publishPresenceChange(UUID userId, String username, String status) {
        // DISABLED for high-performance scale.
        // Global presence broadcast is an anti-pattern at O(N^2).
        /*
        try {
            WebSocketMessage presenceMsg = WebSocketMessage.presence(userId, username, status);
            String json = objectMapper.writeValueAsString(presenceMsg);
            redisTemplate.convertAndSend("chat:presence", json);
        } catch (Exception e) {
            log.error("Failed to publish presence change for {}", userId, e);
        }
        */
    }
}
