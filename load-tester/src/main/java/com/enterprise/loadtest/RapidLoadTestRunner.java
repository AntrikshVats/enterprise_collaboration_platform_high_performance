package com.enterprise.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RapidLoadTestRunner {

    static final int TARGET = 10_000;
    static final int RAMP_RATE_PER_SEC = 50; // Ramp: ~3.3 mins to hit 10k
    static final String BASE_URL = "http://localhost:8080";
    static final String WS_URL = "ws://localhost:8080/ws";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    static class UserSession {
        final Channel channel;
        final String channelId;
        UserSession(Channel channel, String channelId) {
            this.channel = channel;
            this.channelId = channelId;
        }
    }

    private static volatile String lastRegError = "None";
    private static final AtomicInteger regErrorCount = new AtomicInteger(0);
    private static final AtomicInteger debugPrintCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("  REALISTIC 50k LOAD TEST (DASHBOARD MODE)       ");
        System.out.println("=================================================");

        // 1. Pre-register users
        System.out.println("[0:00] Pre-registering " + TARGET + " users via REST API...");
        List<String> tokens = preRegisterUsers(TARGET);
        System.out.println("       Registration complete. " + tokens.size() + " tokens acquired.");

        // 2. Setup Netty NioEventLoopGroup
        EventLoopGroup group = new NioEventLoopGroup(16);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(
                                new HttpClientCodec(),
                                new HttpObjectAggregator(8192)
                        );
                    }
                });

        // 3. Begin Connection Storm
        System.out.println("[0:30] Initiating connection storm...");
        System.out.println("       Target: " + TARGET + " connections at " + RAMP_RATE_PER_SEC + " conn/sec");
        System.out.println("\n--- LIVE DASHBOARD ---");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
        AtomicInteger connectionIndex = new AtomicInteger(0);
        List<UserSession> activeSessions = new CopyOnWriteArrayList<>();

        // Ramp Up Task
        scheduler.scheduleAtFixedRate(() -> {
            for (int i = 0; i < RAMP_RATE_PER_SEC; i++) {
                int id = connectionIndex.getAndIncrement();
                if (id < tokens.size()) {
                    try {
                        String token = tokens.get(id);
                        URI uri = new URI(WS_URL + "?token=" + token);
                        WebSocketClientHandler handler = new WebSocketClientHandler(
                                WebSocketClientHandshakerFactory.newHandshaker(
                                        uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders()
                                )
                        );

                        ChannelFuture future = bootstrap.connect(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort());
                        final String userChannelId = getChannelForUser(id);
                        
                        future.addListener((ChannelFutureListener) f -> {
                            if (f.isSuccess()) {
                                Channel ch = f.channel();
                                ch.pipeline().addLast("ws-handler", handler);
                                handler.handshakeFuture().addListener(hf -> {
                                    if (hf.isSuccess()) {
                                        activeSessions.add(new UserSession(ch, userChannelId));
                                        // Subscribe to their designated channel
                                        ch.writeAndFlush(new TextWebSocketFrame("{\"type\":\"SUBSCRIBE\",\"channelId\":\"" + userChannelId + "\"}"));
                                    }
                                });
                            } else {
                                MetricsCollector.connectionFailures.incrementAndGet();
                            }
                        });
                    } catch (Exception e) {
                        MetricsCollector.connectionFailures.incrementAndGet();
                    }
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

        // 4. Periodic Messaging
        scheduler.scheduleAtFixedRate(() -> {
            if (activeSessions.isEmpty()) return;
            // Send exactly 500 messages per second distributed across random active clients
            int toSend = Math.min(500, activeSessions.size());
            for (int i = 0; i < toSend; i++) {
                UserSession session = activeSessions.get(ThreadLocalRandom.current().nextInt(activeSessions.size()));
                if (session.channel.isActive()) {
                    long now = System.currentTimeMillis();
                    String msg = String.format("{\"type\":\"MESSAGE\",\"channelId\":\"%s\",\"content\":\"loadtest|%d\"}", session.channelId, now);
                    session.channel.writeAndFlush(new TextWebSocketFrame(msg));
                    MetricsCollector.totalMessagesSent.incrementAndGet();
                }
            }
        }, 10, 1, TimeUnit.SECONDS);

        // Print initial newlines for dashboard
        System.out.println("\n\n\n\n\n\n");

        // 5. Live Dashboard Task
        scheduler.scheduleAtFixedRate(() -> {
            MetricsCollector.DashboardMetrics metrics = MetricsCollector.getSnapshotAndResetRolling();
            int active = MetricsCollector.activeConnections.get();
            int failed = MetricsCollector.connectionFailures.get();
            
            // ANSI escape sequence to move cursor up 6 lines and clear below
            System.out.print("\033[6A\033[J");
            System.out.printf("Active Connections : %,d / %,d\n", active, TARGET);
            System.out.printf("Failures           : %,d\n", failed);
            System.out.printf("Reg Errors         : %,d (Last: %s)\n", regErrorCount.get(), lastRegError);
            System.out.printf("Throughput (Sent)  : %,d msg/sec\n", metrics.sentPerSec);
            System.out.printf("Throughput (Rcvd)  : %,d msg/sec\n", metrics.rcvdPerSec);
            System.out.printf("Latency (Live)     : p50 = %d ms | p99 = %d ms\n", metrics.p50LatencyMs, metrics.p99LatencyMs);
            System.out.println("---------------------------------------------------");
            
        }, 2, 2, TimeUnit.SECONDS);

        // 6. Wait for 7 minutes total (5.5m ramp + 1.5m soak)
        Thread.sleep(420_000);

        scheduler.shutdown();
        group.shutdownGracefully();

        System.out.print("\033[6A\033[J"); // clear dashboard
        System.out.println("\n[7:00] Test completed. Shutting down...");
        System.out.println("=================================================");
        System.out.println("  FINAL END-TO-END METRICS                       ");
        System.out.println("=================================================");
        System.out.println("Peak Connections  : " + MetricsCollector.activeConnections.get());
        System.out.println("Total Sent        : " + MetricsCollector.totalMessagesSent.get());
        System.out.println("Total Received    : " + MetricsCollector.totalMessagesReceived.get());
        System.out.println("\nCumulative Latency Percentiles (ms):");
        MetricsCollector.latencyHistogram.outputPercentileDistribution(System.out, 1.0);
        System.exit(0);
    }

    private static String getChannelForUser(int userId) {
        int personalThreshold = (int) (TARGET * 0.90);
        int sparseThreshold = (int) (TARGET * 0.975);

        if (userId < personalThreshold) {
            int channelNum = (userId / 2) + 1;
            return String.format("00000000-0000-1111-0000-%012d", channelNum);
        } else if (userId < sparseThreshold) {
            int offset = userId - personalThreshold;
            int channelNum = (offset / 20) + 1;
            return String.format("00000000-0000-2222-0000-%012d", channelNum);
        } else {
            int offset = userId - sparseThreshold;
            int channelNum = (offset / 450) + 1; // less than 500
            return String.format("00000000-0000-3333-0000-%012d", channelNum);
        }
    }

    private static List<String> preRegisterUsers(int count) throws Exception {
        List<String> tokens = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    String username = "loaduser_" + id;
                    String json = String.format("{\"username\":\"%s\",\"password\":\"test1234\",\"displayName\":\"Load User %d\"}", username, id);
                    
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/auth/register"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 201) {
                        JsonNode node = mapper.readTree(resp.body());
                        tokens.add(node.get("token").asText());
                    } else if (resp.statusCode() == 400) { 
                        HttpRequest reqLogin = HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/api/auth/login"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"username\":\"%s\",\"password\":\"test1234\"}", username)))
                                .build();
                        HttpResponse<String> respLogin = httpClient.send(reqLogin, HttpResponse.BodyHandlers.ofString());
                        if (respLogin.statusCode() == 200) {
                            JsonNode node = mapper.readTree(respLogin.body());
                            tokens.add(node.get("token").asText());
                        } else {
                            if (debugPrintCount.incrementAndGet() <= 10) {
                                System.err.println("Login Failed! Status: " + respLogin.statusCode() + " Body: " + respLogin.body());
                            }
                        }
                    } else {
                        if (debugPrintCount.incrementAndGet() <= 10) {
                            System.err.println("Register Failed! Status: " + resp.statusCode() + " Body: " + resp.body());
                        }
                    }
                } catch (Exception e) {
                    regErrorCount.incrementAndGet();
                    lastRegError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    if (id == 0) {
                        System.err.println("Registration failed for user 0: " + e.getMessage());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(600, TimeUnit.SECONDS);
        executor.shutdown();
        return tokens;
    }
}
