package com.enterprise.chat.service;

import com.enterprise.chat.dto.ChannelDTO;
import com.enterprise.chat.entity.Channel;
import com.enterprise.chat.entity.Channel.ChannelType;
import com.enterprise.chat.entity.ChannelMember;
import com.enterprise.chat.entity.ChannelMember.MemberRole;
import com.enterprise.chat.repository.ChannelMemberRepository;
import com.enterprise.chat.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;

    public ChannelService(ChannelRepository channelRepository,
                          ChannelMemberRepository channelMemberRepository) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
    }

    @Transactional
    public ChannelDTO createChannel(String name, String description,
                                     String type, UUID createdBy) {
        ChannelType channelType = ChannelType.valueOf(type.toUpperCase());

        Channel channel = new Channel(name, description, channelType, createdBy);
        channel = channelRepository.save(channel);

        // Creator auto-joins as OWNER
        ChannelMember membership = new ChannelMember(
                channel.getId(), createdBy, MemberRole.OWNER);
        channelMemberRepository.save(membership);

        return toDTO(channel, 1);
    }

    public List<ChannelDTO> getUserChannels(UUID userId) {
        List<Channel> channels = channelRepository.findChannelsByUserId(userId);
        return channels.stream()
                .map(c -> {
                    long count = channelMemberRepository.countByChannelId(c.getId());
                    return toDTO(c, count);
                })
                .collect(Collectors.toList());
    }

    public List<ChannelDTO> getPublicChannels() {
        List<Channel> channels = channelRepository.findPublicChannels();
        return channels.stream()
                .map(c -> {
                    long count = channelMemberRepository.countByChannelId(c.getId());
                    return toDTO(c, count);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void joinChannel(UUID channelId, UUID userId) {
        if (channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            return; // Already a member
        }
        ChannelMember membership = new ChannelMember(channelId, userId, MemberRole.MEMBER);
        channelMemberRepository.save(membership);
    }

    @Transactional
    public void leaveChannel(UUID channelId, UUID userId) {
        channelMemberRepository.deleteByChannelIdAndUserId(channelId, userId);
    }

    public List<UUID> getChannelMemberIds(UUID channelId) {
        return channelMemberRepository.findUserIdsByChannelId(channelId);
    }

    private ChannelDTO toDTO(Channel channel, long memberCount) {
        ChannelDTO dto = new ChannelDTO(
                channel.getId(),
                channel.getName(),
                channel.getDescription(),
                channel.getType().name(),
                channel.getCreatedBy(),
                channel.getCreatedAt());
        dto.setMemberCount(memberCount);
        return dto;
    }
}
