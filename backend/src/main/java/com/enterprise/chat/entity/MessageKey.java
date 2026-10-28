package com.enterprise.chat.entity;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@PrimaryKeyClass
public class MessageKey implements Serializable {

    @PrimaryKeyColumn(name = "channel_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID channelId;

    @PrimaryKeyColumn(name = "created_at", ordinal = 1, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant createdAt;

    @PrimaryKeyColumn(name = "id", ordinal = 2, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private UUID id;

    public MessageKey() {}

    public MessageKey(UUID channelId, Instant createdAt, UUID id) {
        this.channelId = channelId;
        this.createdAt = createdAt;
        this.id = id;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public void setChannelId(UUID channelId) {
        this.channelId = channelId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageKey that = (MessageKey) o;
        return Objects.equals(channelId, that.channelId) &&
               Objects.equals(createdAt, that.createdAt) &&
               Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId, createdAt, id);
    }
}
