package com.enterprise.chat.entity;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("messages")
public class Message {

    @PrimaryKey
    private MessageKey key;

    @Column("sender_id")
    private UUID senderId;

    @Column("content")
    private String content;

    public Message() {}

    public Message(UUID channelId, UUID senderId, String content) {
        this.key = new MessageKey(channelId, Instant.now(), UUID.randomUUID());
        this.senderId = senderId;
        this.content = content;
    }

    public MessageKey getKey() {
        return key;
    }

    public void setKey(MessageKey key) {
        this.key = key;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public void setSenderId(UUID senderId) {
        this.senderId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // Helper methods for backwards compatibility
    public UUID getId() {
        return key != null ? key.getId() : null;
    }

    public UUID getChannelId() {
        return key != null ? key.getChannelId() : null;
    }

    public Instant getCreatedAt() {
        return key != null ? key.getCreatedAt() : null;
    }
}
