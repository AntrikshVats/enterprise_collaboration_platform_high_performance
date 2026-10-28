package com.enterprise.chat.controller;

import com.enterprise.chat.dto.ChannelDTO;
import com.enterprise.chat.dto.WebSocketMessage;
import com.enterprise.chat.service.ChannelService;
import com.enterprise.chat.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService channelService;
    private final MessageService messageService;

    public ChannelController(ChannelService channelService, MessageService messageService) {
        this.channelService = channelService;
        this.messageService = messageService;
    }

    @GetMapping
    public ResponseEntity<List<ChannelDTO>> getMyChannels(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(channelService.getUserChannels(userId));
    }

    @GetMapping("/public")
    public ResponseEntity<List<ChannelDTO>> getPublicChannels() {
        return ResponseEntity.ok(channelService.getPublicChannels());
    }

    @PostMapping
    public ResponseEntity<ChannelDTO> createChannel(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        String name = body.getOrDefault("name", "new-channel");
        String description = body.getOrDefault("description", "");
        String type = body.getOrDefault("type", "PUBLIC");

        ChannelDTO channel = channelService.createChannel(name, description, type, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(channel);
    }

    @PostMapping("/{channelId}/join")
    public ResponseEntity<Void> joinChannel(
            @PathVariable UUID channelId,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        channelService.joinChannel(channelId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{channelId}/leave")
    public ResponseEntity<Void> leaveChannel(
            @PathVariable UUID channelId,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        channelService.leaveChannel(channelId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{channelId}/messages")
    public ResponseEntity<List<WebSocketMessage>> getMessages(
            @PathVariable UUID channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(messageService.getChannelHistory(channelId, page, size));
    }
}
