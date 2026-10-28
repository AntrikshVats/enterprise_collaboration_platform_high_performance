package com.enterprise.chat.controller;

import com.enterprise.chat.dto.UserDTO;
import com.enterprise.chat.service.PresenceService;
import com.enterprise.chat.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PresenceService presenceService;

    public UserController(UserService userService, PresenceService presenceService) {
        this.userService = userService;
        this.presenceService = presenceService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getMyProfile(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO> getUserProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserDTO>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(userService.searchUsers(q));
    }

    @GetMapping("/online")
    public ResponseEntity<Set<String>> getOnlineUsers() {
        return ResponseEntity.ok(presenceService.getOnlineUserIds());
    }
}
