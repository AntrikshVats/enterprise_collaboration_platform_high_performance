package com.enterprise.chat.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "channel_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "user_id"}))
public class ChannelMember {

    public enum MemberRole {
        OWNER, ADMIN, MEMBER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "joined_at")
    private Instant joinedAt;

    public ChannelMember() {}

    public ChannelMember(UUID channelId, UUID userId, MemberRole role) {
        this.channelId = channelId;
        this.userId = userId;
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        joinedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getChannelId() { return channelId; }
    public void setChannelId(UUID channelId) { this.channelId = channelId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public MemberRole getRole() { return role; }
    public void setRole(MemberRole role) { this.role = role; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
