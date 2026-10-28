package com.enterprise.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;

public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private static final ObjectMapper mapper = new ObjectMapper();

    public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        MetricsCollector.activeConnections.decrementAndGet();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                handshakeFuture.setSuccess();
                MetricsCollector.activeConnections.incrementAndGet();
            } catch (WebSocketHandshakeException e) {
                handshakeFuture.setFailure(e);
                MetricsCollector.connectionFailures.incrementAndGet();
            }
            return;
        }

        if (msg instanceof FullHttpResponse) {
            throw new IllegalStateException("Unexpected FullHttpResponse");
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {
            MetricsCollector.totalMessagesReceived.incrementAndGet();
            String text = ((TextWebSocketFrame) frame).text();
            
            try {
                JsonNode node = mapper.readTree(text);
                if ("MESSAGE".equals(node.path("type").asText()) && node.has("content")) {
                    String content = node.get("content").asText();
                    if (content.startsWith("loadtest|")) {
                        long sendTime = Long.parseLong(content.split("\\|")[1]);
                        MetricsCollector.recordLatency(sendTime);
                    }
                }
            } catch (Exception e) {
                // Ignore parse errors for performance
            }
        } else if (frame instanceof PongWebSocketFrame) {
            // Heartbeat
        } else if (frame instanceof CloseWebSocketFrame) {
            ch.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
            MetricsCollector.connectionFailures.incrementAndGet();
        }
        ctx.close();
    }
}
