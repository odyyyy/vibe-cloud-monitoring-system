package com.monitoring.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window, per-user rate limiter for the global search endpoint.
 *
 * <p>Search fans out into one bounded query per resource type, so it is more expensive than a
 * single list request. State is in-memory and resets on restart; it bounds accidental client
 * loops, not security-sensitive operations. Expired windows are evicted opportunistically so a
 * long-lived process cannot accumulate entries without bound.
 */
@Component
public class SearchRateLimiter {

    private static final int MAX_TRACKED_USERS = 10_000;

    private final int maxRequests;
    private final Duration window;
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    public SearchRateLimiter(
            @Value("${monitoring.search.rate-limit.max-requests:30}") int maxRequests,
            @Value("${monitoring.search.rate-limit.window-seconds:60}") int windowSeconds) {
        this.maxRequests = Math.max(1, maxRequests);
        this.window = Duration.ofSeconds(Math.max(1, windowSeconds));
    }

    /**
     * @return seconds to wait when the caller is over the limit, or {@code 0} when the request
     *         may proceed
     */
    public long tryAcquire(Long userId, Instant now) {
        if (userId == null) {
            return 0;
        }
        evictExpiredIfNeeded(now);
        Window current = windows.computeIfAbsent(userId, ignored -> new Window(now));
        synchronized (current) {
            if (!now.isBefore(current.windowStart.plus(window))) {
                current.windowStart = now;
                current.count = 0;
            }
            if (current.count >= maxRequests) {
                return Math.max(1, Duration.between(now, current.windowStart.plus(window)).toSeconds());
            }
            current.count++;
            return 0;
        }
    }

    private void evictExpiredIfNeeded(Instant now) {
        if (windows.size() < MAX_TRACKED_USERS) {
            return;
        }
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().windowStart.plus(window)));
    }

    private static final class Window {
        private Instant windowStart;
        private int count;

        private Window(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
