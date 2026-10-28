package com.enterprise.chat.service;

import com.enterprise.chat.dto.WebSocketMessage;
import com.enterprise.chat.entity.Message;
import com.enterprise.chat.repository.MessageRepository;
import com.enterprise.chat.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get paginated message history for a channel.
     * Returns messages in chronological order (oldest first) for display.
     */
    public List<WebSocketMessage> getChannelHistory(UUID channelId, int page, int size) {
        List<Message> messages = messageRepository
                .findByKeyChannelId(channelId, PageRequest.of(page, size)).getContent();

        // Collect unique sender IDs for batch user lookup
        Set<UUID> senderIds = new HashSet<>();
        for (Message msg : messages) {
            senderIds.add(msg.getSenderId());
        }

        // Batch fetch usernames
        Map<UUID, String> usernameMap = new HashMap<>();
        if (!senderIds.isEmpty()) {
            userRepository.findAllByIdIn(new ArrayList<>(senderIds))
                    .forEach(user -> usernameMap.put(user.getId(), user.getDisplayName()));
        }

        // Convert to DTOs in chronological order (reverse the DESC query)
        List<WebSocketMessage> result = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            result.add(WebSocketMessage.chatMessage(
                    msg.getId(),
                    msg.getChannelId(),
                    msg.getSenderId(),
                    usernameMap.getOrDefault(msg.getSenderId(), "Unknown"),
                    msg.getContent(),
                    msg.getCreatedAt().toEpochMilli()));
        }

        return result;
    }
}
