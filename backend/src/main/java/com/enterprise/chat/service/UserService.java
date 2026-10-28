package com.enterprise.chat.service;

import com.enterprise.chat.dto.UserDTO;
import com.enterprise.chat.entity.User;
import com.enterprise.chat.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("null")
public class UserService {

    private final UserRepository userRepository;
    private final PresenceService presenceService;

    public UserService(UserRepository userRepository, PresenceService presenceService) {
        this.userRepository = userRepository;
        this.presenceService = presenceService;
    }

    public UserDTO getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserDTO dto = toDTO(user);
        dto.setOnline(presenceService.isOnline(userId));
        return dto;
    }

    public List<UserDTO> searchUsers(String query) {
        List<User> users = userRepository.findByUsernameContainingIgnoreCase(query);
        return users.stream()
                .map(u -> {
                    UserDTO dto = toDTO(u);
                    dto.setOnline(presenceService.isOnline(u.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private UserDTO toDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl());
    }
}
