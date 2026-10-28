package com.enterprise.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Redis Pub/Sub listener that receives messages published by any server instance
 * and broadcasts them to locally connected WebSocket clients.
 *
 * This is the key component for horizontal scalability:
 * - Instance A receives a chat message → publishes to Redis
 * - All instances (including A) receive the Redis message
 * - Each instance broadcasts to its locally connected clients
 *
 * Channel patterns:
 * - chat:channel:* → Chat messages (routed to channel subscribers)
 * - chat:typing:*  → Typing indicators (routed to channel subscribers)
 * - chat:presence  → Presence updates (broadcast to all connected clients)
 */
@Component
@SuppressWarnings("null")
public class RedisMessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageListener.class);

    private final RedisMessageListenerContainer container;
    private final SessionRegistry sessionRegistry;

    public RedisMessageListener(RedisMessageListenerContainer container,
                                 SessionRegistry sessionRegistry) {
        this.container = container;
        this.sessionRegistry = sessionRegistry;
    }

    @PostConstruct
    public void init() {
        // Subscribe to all chat message channels using pattern
        container.addMessageListener(chatMessageListener(),
                new PatternTopic("chat:channel:*"));

        // Subscribe to all typing indicator channels using pattern
        container.addMessageListener(typingListener(),
                new PatternTopic("chat:typing:*"));

        // Subscribe to the global presence channel
        container.addMessageListener(presenceListener(),
                new ChannelTopic("chat:presence"));

        log.info("Redis message listeners registered");
    }

    private MessageListener chatMessageListener() {
        return (message, pattern) -> {
            try {
                String channel = new String(message.getChannel());
                String channelId = channel.replace("chat:channel:", "");
                String json = new String(message.getBody());

                // Broadcast to all local sessions subscribed to this channel
                sessionRegistry.broadcastToChannel(UUID.fromString(channelId), json);
            } catch (Exception e) {
                log.error("Error processing chat message from Redis", e);
            }
        };
    }

    private MessageListener typingListener() {
        return (message, pattern) -> {
            try {
                String channel = new String(message.getChannel());
                String channelId = channel.replace("chat:typing:", "");
                String json = new String(message.getBody());

                sessionRegistry.broadcastToChannel(UUID.fromString(channelId), json);
            } catch (Exception e) {
                log.error("Error processing typing event from Redis", e);
            }
        };
    }

    private MessageListener presenceListener() {
        return (message, pattern) -> {
            // DISABLED for high-performance scale.
            /*
            try {
                String json = new String(message.getBody());
                // Presence events go to ALL connected clients
                sessionRegistry.broadcastToAll(json);
            } catch (Exception e) {
                log.error("Error processing presence event from Redis", e);
            }
            */
        };
    }
}
