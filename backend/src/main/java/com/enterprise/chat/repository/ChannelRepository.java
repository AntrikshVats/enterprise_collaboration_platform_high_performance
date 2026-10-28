package com.enterprise.chat.repository;

import com.enterprise.chat.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    List<Channel> findByType(Channel.ChannelType type);

    @Query("SELECT c FROM Channel c JOIN ChannelMember cm ON c.id = cm.channelId " +
           "WHERE cm.userId = :userId ORDER BY c.name ASC")
    List<Channel> findChannelsByUserId(@Param("userId") UUID userId);

    @Query("SELECT c FROM Channel c WHERE c.type = 'PUBLIC' ORDER BY c.name ASC")
    List<Channel> findPublicChannels();
}
