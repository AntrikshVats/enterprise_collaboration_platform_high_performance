package com.enterprise.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Unified WebSocket message envelope.
 * Used for both inbound (client → server) and outbound (server → client) messages.
 *
 * Inbound types: MESSAGE, TYPING, SUBSCRIBE, UNSUBSCRIBE, HEARTBEAT
 * Outbound types: MESSAGE, TYPING, PRESENCE, ACK, ERROR, CHANNEL_UPDATE
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {

    public enum MessageType {
        MESSAGE, TYPING, PRESENCE, SUBSCRIBE, UNSUBSCRIBE,
        HEARTBEAT, ACK, ERROR, CHANNEL_UPDATE
    }

    private MessageType type;
    private String id;
    private String channelId;
    private String senderId;
    private String senderName;
    private String content;
    private Long timestamp;
    private String status;      // for PRESENCE: ONLINE/OFFLINE
    private String userId;      // for PRESENCE events
    private String username;    // for TYPING events
    private Integer code;       // for ERROR
    private String message;     // for ERROR

    public WebSocketMessage() {}

    // Static factory methods for common outbound messages
    public static WebSocketMessage chatMessage(UUID id, UUID channelId, UUID senderId,
                                                String senderName, String content, long timestamp) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.type = MessageType.MESSAGE;
        msg.id = id.toString();
        msg.channelId = channelId.toString();
        msg.senderId = senderId.toString();
        msg.senderName = senderName;
        msg.content = content;
        msg.timestamp = timestamp;
        return msg;
    }

    public static WebSocketMessage typing(UUID channelId, UUID userId, String username) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.type = MessageType.TYPING;
        msg.channelId = channelId.toString();
        msg.userId = userId.toString();
        msg.username = username;
        msg.timestamp = System.currentTimeMillis();
        return msg;
    }

    public static WebSocketMessage presence(UUID userId, String username, String status) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.type = MessageType.PRESENCE;
        msg.userId = userId.toString();
        msg.username = username;
        msg.status = status;
        msg.timestamp = System.currentTimeMillis();
        return msg;
    }

    public static WebSocketMessage ack(UUID messageId) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.type = MessageType.ACK;
        msg.id = messageId.toString();
        msg.timestamp = System.currentTimeMillis();
        return msg;
    }

    public static WebSocketMessage error(int code, String message) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.type = MessageType.ERROR;
        msg.code = code;
        msg.message = message;
        msg.timestamp = System.currentTimeMillis();
        return msg;
    }

    // Getters and Setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
