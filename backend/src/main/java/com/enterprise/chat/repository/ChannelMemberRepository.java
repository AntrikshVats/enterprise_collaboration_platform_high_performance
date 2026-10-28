package com.enterprise.chat.repository;

import com.enterprise.chat.entity.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, UUID> {

    List<ChannelMember> findByChannelId(UUID channelId);

    List<ChannelMember> findByUserId(UUID userId);

    Optional<ChannelMember> findByChannelIdAndUserId(UUID channelId, UUID userId);

    boolean existsByChannelIdAndUserId(UUID channelId, UUID userId);

    void deleteByChannelIdAndUserId(UUID channelId, UUID userId);

    long countByChannelId(UUID channelId);

    @Query("SELECT cm.userId FROM ChannelMember cm WHERE cm.channelId = :channelId")
    List<UUID> findUserIdsByChannelId(@Param("channelId") UUID channelId);
}
