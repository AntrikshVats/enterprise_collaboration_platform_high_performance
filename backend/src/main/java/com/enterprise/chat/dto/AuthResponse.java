package com.enterprise.chat.dto;

import java.util.UUID;

public class AuthResponse {

    private String token;
    private UUID userId;
    private String username;
    private String displayName;
    private String avatarUrl;

    public AuthResponse() {}

    public AuthResponse(String token, UUID userId, String username, String displayName, String avatarUrl) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
