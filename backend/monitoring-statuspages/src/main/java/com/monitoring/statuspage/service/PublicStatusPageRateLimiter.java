package com.monitoring.statuspage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PublicStatusPageRateLimiter {

    private static final long WINDOW_SECONDS = 60L;
    private static final int MAX_KEY_LENGTH = 64;

    private final int maxRequests;
    private final int maxTrackedKeys;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public PublicStatusPageRateLimiter(
            @Value("${monitoring.status-pages.public.max-requests-per-minute:120}") int maxRequests,
            @Value("${monitoring.status-pages.public.max-tracked-keys:100000}") int maxTrackedKeys) {
        this.maxRequests = Math.max(1, maxRequests);
        this.maxTrackedKeys = Math.max(1, maxTrackedKeys);
    }

    public long tryAcquire(String clientIp, Instant now) {
        String key = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        if (key.length() > MAX_KEY_LENGTH) {
            key = key.substring(0, MAX_KEY_LENGTH);
        }

        long windowMinute = windowMinute(now);
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= maxTrackedKeys) {
                evictStale(windowMinute);
                if (windows.size() >= maxTrackedKeys) {
                    return 0;
                }
            }
            window = windows.computeIfAbsent(key, ignored -> new Window(windowMinute));
        }

        if (window.increment(windowMinute) > maxRequests) {
            return retryAfterSeconds(now);
        }
        return 0;
    }

    @Scheduled(fixedDelay = 60_000L)
    void evictStaleWindows() {
        evictStale(windowMinute(Instant.now()));
    }

    private void evictStale(long currentMinute) {
        windows.entrySet().removeIf(entry -> entry.getValue().isStale(currentMinute));
    }

    private static long windowMinute(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), WINDOW_SECONDS);
    }

    private static long retryAfterSeconds(Instant now) {
        return WINDOW_SECONDS - Math.floorMod(now.getEpochSecond(), WINDOW_SECONDS);
    }

    private static final class Window {

        private final AtomicInteger count = new AtomicInteger();
        private volatile long minute;

        private Window(long minute) {
            this.minute = minute;
        }

        private int increment(long currentMinute) {
            if (minute == currentMinute) {
                return count.incrementAndGet();
            }
            synchronized (this) {
                if (minute != currentMinute) {
                    minute = currentMinute;
                    count.set(0);
                }
                return count.incrementAndGet();
            }
        }

        private boolean isStale(long currentMinute) {
            return minute < currentMinute;
        }
    }
}
