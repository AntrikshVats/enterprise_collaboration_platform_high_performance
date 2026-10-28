package com.enterprise.chat.dto;

import java.time.Instant;
import java.util.UUID;

public class ChannelDTO {

    private UUID id;
    private String name;
    private String description;
    private String type;
    private UUID createdBy;
    private Instant createdAt;
    private long memberCount;

    public ChannelDTO() {}

    public ChannelDTO(UUID id, String name, String description, String type,
                      UUID createdBy, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public long getMemberCount() { return memberCount; }
    public void setMemberCount(long memberCount) { this.memberCount = memberCount; }
}
