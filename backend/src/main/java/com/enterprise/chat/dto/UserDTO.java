package com.enterprise.chat.dto;

import java.util.UUID;

public class UserDTO {

    private UUID id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private boolean online;

    public UserDTO() {}

    public UserDTO(UUID id, String username, String displayName, String avatarUrl) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
}
