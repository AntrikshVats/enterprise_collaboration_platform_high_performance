# Performance Guide — Enterprise Chat Platform

## JVM Configuration

### Recommended JVM Flags

```bash
java -XX:+UseZGC \
     -XX:MaxGCPauseMillis=5 \
     -Xms2g -Xmx4g \
     -XX:MaxDirectMemorySize=1g \
     -Djdk.virtualThreadScheduler.parallelism=12 \
     -Djdk.virtualThreadScheduler.maxPoolSize=12 \
     -jar enterprise-chat-1.0.0.jar
```

### Flag Explanation

| Flag | Purpose | Impact |
|------|---------|--------|
| `-XX:+UseZGC` | Z Garbage Collector | Sub-millisecond GC pauses at any heap size |
| `-XX:MaxGCPauseMillis=5` | Target max GC pause | Prevents connection drops during GC |
| `-Xms2g -Xmx4g` | Heap size 2-4 GB | Leaves RAM for OS, Redis, PostgreSQL |
| `-XX:MaxDirectMemorySize=1g` | Direct buffer memory | NIO/WebSocket I/O buffers |
| `virtualThreadScheduler.parallelism=12` | Carrier thread count | Match CPU threads (6 cores × 2 HT) |

### Why ZGC?

At 50,000 concurrent connections, a standard G1GC "stop-the-world" pause of 50-200ms would:
- Cause WebSocket ping/pong timeouts
- Buffer up 25-100 messages during the pause
- Trigger mass client reconnections

ZGC provides <1ms pauses regardless of heap size, making it essential for real-time chat.

---

## Memory Budget (16 GB System)

| Component | Estimated RAM | Notes |
|-----------|-------------|-------|
| JVM Heap (max) | 4.0 GB | Configurable via -Xmx |
| JVM Direct Memory | 1.0 GB | NIO buffers for WebSocket I/O |
| JVM Metaspace + Overhead | 0.5 GB | Class metadata, JIT compiled code |
| 50k WebSocket Sessions | 0.2 GB | ~4 KB per session (session object + attributes) |
| 50k Virtual Thread Stacks | 0.2 GB | ~4 KB initial stack per virtual thread |
| Redis | 1.0 GB | Presence keys + pub/sub overhead |
| PostgreSQL | 2.0 GB | shared_buffers + work_mem |
| OS + Desktop + IDE | 3.0 GB | Windows/Linux + development tools |
| **Buffer / Headroom** | **~4.1 GB** | Safety margin |
| **Total** | **~12 GB / 16 GB** | ✅ Within budget |

---

## Connection Handling Architecture

```
Client WebSocket Connect (ws://host/ws?token=JWT)
        │
        ▼
┌─── Tomcat Acceptor Thread (1 thread) ───┐
│   Accepts TCP connection                 │
│   HTTP Upgrade handshake                 │
└──────────────┬───────────────────────────┘
               │
               ▼
┌─── Virtual Thread (per connection) ──────┐
│   ChatWebSocketHandler.afterConnectionEstablished()  │
│   • Validate JWT                                     │
│   • Register in SessionRegistry                      │
│   • Set presence in Redis                            │
│                                                      │
│   handleTextMessage() [called per message]           │
│   • Parse JSON                                       │
│   • Route by type (MESSAGE/TYPING/SUBSCRIBE/...)     │
│   • Publish to Redis                                 │
│   • Enqueue for async DB write                       │
└──────────────────────────────────────────┘
```

**Why Virtual Threads work here:**
- Each virtual thread is ~4 KB (vs ~1 MB platform thread)
- 50k virtual threads = ~200 MB (vs 50 GB with platform threads)
- Blocking I/O (Redis, DB) automatically unmounts from carrier thread
- No `synchronized` blocks in the hot path (we use `ConcurrentHashMap`)

---

## Database Optimization

### Batch Write Strategy

```
WebSocket Message Received
        │
        ├──▶ Redis PUBLISH (instant broadcast)
        │
        └──▶ MessageBatchWriter.enqueue() (non-blocking)
                    │
                    ▼
             BlockingQueue<Message> (capacity: 100,000)
                    │
                    ▼ (every 100ms)
             flushBatch() → saveAll(batch of 50)
                    │
                    ▼
             PostgreSQL (10 batch INSERTs/s instead of 500 singles/s)
```

**Impact:**
- Message broadcast latency: ~2-5ms (Redis only)
- Database I/O reduced by 50x (500 → 10 operations/second)
- Connection pool utilization: ~5% (vs ~100% with sync writes)

### HikariCP Configuration

```yaml
hikari:
  maximum-pool-size: 20    # Small pool — virtual threads don't need 1:1
  minimum-idle: 5           # Keep some warm connections
  connection-timeout: 10000 # 10s timeout
```

**Why 20 connections?** Virtual threads queue up waiting for a connection rather than consuming a platform thread each. 20 connections is enough to handle batch writes + REST API queries without contention.

---

## Redis Pub/Sub Pattern

```
Instance A                    Redis                    Instance B
     │                          │                          │
     │  PUBLISH chat:ch:123     │                          │
     │─────────────────────────▶│                          │
     │                          │  SUBSCRIBE chat:ch:*     │
     │                          │─────────────────────────▶│
     │                          │                          │
     │  SUBSCRIBE chat:ch:*     │                          │
     │◀─────────────────────────│                          │
     │                          │                          │
     │  Broadcast to local      │  Broadcast to local      │
     │  sessions in ch:123      │  sessions in ch:123      │
```

**Channel patterns:**
- `chat:channel:{channelId}` — Chat messages (per-channel routing)
- `chat:typing:{channelId}` — Typing indicators (ephemeral, no persistence)
- `chat:presence` — Global presence changes (broadcast to all)

---

## Monitoring Recommendations

### Key Metrics to Track

1. **WebSocket Connections**: `SessionRegistry.getActiveSessionCount()`
2. **Message Throughput**: Count messages per second through Redis pub/sub
3. **Batch Writer Buffer Size**: `MessageBatchWriter.getBufferSize()` — should stay < 1000
4. **HikariCP Active Connections**: Via Micrometer metrics
5. **GC Pause Time**: ZGC should show < 1ms p99

### Adding Micrometer (Optional)

Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

---

## Load Testing

### WebSocket Load Test with Python

```python
import asyncio
import websockets
import json

async def connect_client(token, channel_id, client_id):
    uri = f"ws://localhost:8080/ws?token={token}"
    async with websockets.connect(uri) as ws:
        # Subscribe to channel
        await ws.send(json.dumps({
            "type": "SUBSCRIBE",
            "channelId": channel_id
        }))
        # Send periodic messages
        for i in range(100):
            await ws.send(json.dumps({
                "type": "MESSAGE",
                "channelId": channel_id,
                "content": f"Test message {i} from client {client_id}"
            }))
            await asyncio.sleep(0.1)  # 10 msg/s per client

async def main():
    # Create 1000 concurrent connections
    tasks = [connect_client(TOKEN, CHANNEL_ID, i) for i in range(1000)]
    await asyncio.gather(*tasks)

asyncio.run(main())
```
