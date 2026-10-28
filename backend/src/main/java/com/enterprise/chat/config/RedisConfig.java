package com.enterprise.chat.config;

import com.enterprise.chat.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration:
 * - StringRedisTemplate for presence keys (simple string values)
 * - RedisTemplate<String, WebSocketMessage> for pub/sub (JSON serialization)
 * - RedisMessageListenerContainer for subscribing to channels
 *
 * Uses Lettuce (default Spring Boot Redis client) with connection pooling
 * configured in application.yml.
 */
@Configuration
@SuppressWarnings("null")
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Dedicated template for pub/sub message serialization.
     * Messages are serialized as JSON for cross-instance compatibility.
     */
    @Bean
    public RedisTemplate<String, WebSocketMessage> messageRedisTemplate(
            RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, WebSocketMessage> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());

        Jackson2JsonRedisSerializer<WebSocketMessage> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, WebSocketMessage.class);
        template.setValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Listener container for Redis pub/sub.
     * Runs on virtual threads to handle high-throughput message delivery.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // Use virtual thread executor for listener callbacks
        container.setTaskExecutor(command ->
                Thread.ofVirtual().name("redis-listener").start(command));
        return container;
    }
}
