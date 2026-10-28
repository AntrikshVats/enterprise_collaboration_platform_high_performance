package com.enterprise.chat.websocket;

import com.enterprise.chat.entity.Message;
import com.enterprise.chat.repository.MessageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Asynchronous batch writer for chat messages.
 *
 * Design rationale:
 * - Messages are first broadcast via Redis for instant delivery (~0ms impact)
 * - Database persistence is decoupled: messages are queued in-memory
 * - A scheduled task flushes the queue every 100ms in batches of 50
 * - This reduces DB I/O from 500 INSERT/s to ~10 batch INSERT/s (50x reduction)
 * - Uses JPA saveAll() which leverages Hibernate batch_size=50 for efficient writes
 *
 * Risk: ~100ms of messages could be lost on JVM crash.
 * Acceptable for chat (messages were already delivered via Redis).
 * For critical audit logs, use synchronous writes instead.
 */
@Component
public class MessageBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(MessageBatchWriter.class);

    private final MessageRepository messageRepository;
    private final BlockingQueue<Message> buffer = new LinkedBlockingQueue<>(100_000);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("msg-batch-writer").factory());

    @Value("${app.batch.size:50}")
    private int batchSize;

    @Value("${app.batch.flush-interval-ms:100}")
    private long flushIntervalMs;

    public MessageBatchWriter(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(this::flushBatch,
                flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        log.info("MessageBatchWriter started (batch={}, interval={}ms)", batchSize, flushIntervalMs);
    }

    @PreDestroy
    public void stop() {
        // Flush remaining messages before shutdown
        flushBatch();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("MessageBatchWriter stopped. Remaining in buffer: {}", buffer.size());
    }

    /**
     * Enqueue a message for asynchronous persistence.
     * Non-blocking: returns immediately.
     */
    public void enqueue(Message message) {
        if (!buffer.offer(message)) {
            log.error("Message buffer full! Dropping message {}", message.getKey() != null ? message.getKey().getId() : "unknown");
        }
    }

    /**
     * Drain up to batchSize messages from the queue and persist them.
     */
    private void flushBatch() {
        if (buffer.isEmpty()) return;

        List<Message> batch = new ArrayList<>(batchSize);
        buffer.drainTo(batch, batchSize);

        if (!batch.isEmpty()) {
            try {
                messageRepository.saveAll(batch);
                log.debug("Flushed {} messages to database", batch.size());
            } catch (Exception e) {
                log.error("Failed to flush message batch ({} messages)", batch.size(), e);
                // Re-enqueue failed messages (best effort)
                for (Message msg : batch) {
                    buffer.offer(msg);
                }
            }
        }
    }

    public int getBufferSize() {
        return buffer.size();
    }
}
