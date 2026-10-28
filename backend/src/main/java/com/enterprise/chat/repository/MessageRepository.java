package com.enterprise.chat.repository;

import com.enterprise.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import com.enterprise.chat.entity.MessageKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends CassandraRepository<Message, MessageKey> {

    /**
     * Fetch latest messages for a channel, ordered by creation time descending.
     * With Cassandra, the clustering key automatically handles ordering.
     */
    @Query("SELECT * FROM messages WHERE channel_id = ?0")
    Slice<Message> findByKeyChannelId(UUID channelId, Pageable pageable);

    /**
     * Note: COUNT is expensive in Cassandra. If you need count, consider
     * maintaining a separate counter table. For now, we omit it or provide a basic query.
     */
    @Query("SELECT count(*) FROM messages WHERE channel_id = ?0")
    long countByKeyChannelId(UUID channelId);
}
