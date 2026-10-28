package com.enterprise.chat.service;

import com.enterprise.chat.dto.AuthRequest;
import com.enterprise.chat.dto.AuthResponse;
import com.enterprise.chat.entity.ChannelMember;
import com.enterprise.chat.entity.User;
import com.enterprise.chat.repository.ChannelMemberRepository;
import com.enterprise.chat.repository.UserRepository;
import com.enterprise.chat.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TransactionTemplate transactionTemplate;

    // Dedicated platform thread pool for CPU-bound hashing
    private static final ExecutorService hashExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors());

    // Default general channel ID (from schema.sql seed)
    private static final UUID GENERAL_CHANNEL_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    public AuthService(UserRepository userRepository,
                       ChannelMemberRepository channelMemberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.transactionTemplate = transactionTemplate;
    }

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
        }

        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            email = request.getUsername() + "@enterprise.local";
        }

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }

        String displayName = request.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = request.getUsername();
        }

        // Perform slow CPU-bound BCrypt hash on a platform thread to prevent carrier thread pinning
        String encodedPassword;
        try {
            encodedPassword = hashExecutor.submit(() -> passwordEncoder.encode(request.getPassword())).get();
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }

        final String finalEmail = email;
        final String finalDisplayName = displayName;

        User user = transactionTemplate.execute(status -> {
            User newUser = new User(
                    request.getUsername(),
                    finalEmail,
                    encodedPassword,
                    finalDisplayName);
            newUser = userRepository.save(newUser);

            // Auto-join the #general channel
            ChannelMember membership = new ChannelMember(
                    GENERAL_CHANNEL_ID, newUser.getId(), ChannelMember.MemberRole.MEMBER);
            channelMemberRepository.save(membership);
            return newUser;
        });

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return new AuthResponse(token, user.getId(), user.getUsername(),
                user.getDisplayName(), user.getAvatarUrl());
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        boolean matches;
        try {
            matches = hashExecutor.submit(() -> passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).get();
        } catch (Exception e) {
            throw new RuntimeException("Password verification failed", e);
        }

        if (!matches) {
            throw new RuntimeException("Invalid username or password");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return new AuthResponse(token, user.getId(), user.getUsername(),
                user.getDisplayName(), user.getAvatarUrl());
    }
}
