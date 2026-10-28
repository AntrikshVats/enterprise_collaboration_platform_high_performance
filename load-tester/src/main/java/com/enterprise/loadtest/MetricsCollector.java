package com.enterprise.loadtest;

import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {
    // Cumulative histogram for end-of-test summary
    public static final Histogram latencyHistogram = new ConcurrentHistogram(TimeUnit.MINUTES.toNanos(10), 3);
    
    // Rolling histogram for live dashboard
    private static volatile Histogram rollingHistogram = new ConcurrentHistogram(TimeUnit.MINUTES.toNanos(1), 3);
    
    public static final AtomicInteger activeConnections = new AtomicInteger(0);
    public static final AtomicInteger connectionFailures = new AtomicInteger(0);
    
    public static final AtomicLong totalMessagesSent = new AtomicLong(0);
    public static final AtomicLong totalMessagesReceived = new AtomicLong(0);

    // Throughput tracking
    private static long lastSnapshotTime = System.currentTimeMillis();
    private static long lastSentCount = 0;
    private static long lastReceivedCount = 0;
    
    public static void recordLatency(long sendTimeMillis) {
        long latencyMillis = System.currentTimeMillis() - sendTimeMillis;
        if (latencyMillis < 0) latencyMillis = 0;
        
        latencyHistogram.recordValue(latencyMillis);
        
        Histogram rolling = rollingHistogram;
        if (rolling != null) {
            rolling.recordValue(latencyMillis);
        }
    }
    
    public static synchronized DashboardMetrics getSnapshotAndResetRolling() {
        long now = System.currentTimeMillis();
        long elapsedSecs = (now - lastSnapshotTime) / 1000;
        if (elapsedSecs <= 0) elapsedSecs = 1;
        
        long sent = totalMessagesSent.get();
        long rcvd = totalMessagesReceived.get();
        
        long sentPerSec = (sent - lastSentCount) / elapsedSecs;
        long rcvdPerSec = (rcvd - lastReceivedCount) / elapsedSecs;
        
        Histogram oldHist = rollingHistogram;
        rollingHistogram = new ConcurrentHistogram(TimeUnit.MINUTES.toNanos(1), 3);
        
        long p50 = oldHist.getTotalCount() > 0 ? oldHist.getValueAtPercentile(50.0) : 0;
        long p99 = oldHist.getTotalCount() > 0 ? oldHist.getValueAtPercentile(99.0) : 0;
        
        lastSnapshotTime = now;
        lastSentCount = sent;
        lastReceivedCount = rcvd;
        
        return new DashboardMetrics(sentPerSec, rcvdPerSec, p50, p99);
    }
    
    public static class DashboardMetrics {
        public final long sentPerSec;
        public final long rcvdPerSec;
        public final long p50LatencyMs;
        public final long p99LatencyMs;

        public DashboardMetrics(long sentPerSec, long rcvdPerSec, long p50LatencyMs, long p99LatencyMs) {
            this.sentPerSec = sentPerSec;
            this.rcvdPerSec = rcvdPerSec;
            this.p50LatencyMs = p50LatencyMs;
            this.p99LatencyMs = p99LatencyMs;
        }
    }
}
