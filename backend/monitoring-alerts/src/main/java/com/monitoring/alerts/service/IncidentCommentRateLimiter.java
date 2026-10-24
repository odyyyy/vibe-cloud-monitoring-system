package com.monitoring.alerts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window, per-user rate limiter for incident comments.
 *
 * <p>The map is bounded by an explicit maximum number of tracked users; expired windows are
 * evicted before the map can grow without limit. State is in-memory and resets on restart,
 * which is sufficient for spam prevention in the MVP.
 */
@Component
public class IncidentCommentRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final int maxTrackedUsers;
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();
    private final Object registrationLock = new Object();

    public IncidentCommentRateLimiter(
            @Value("${monitoring.alerts.comments.max-requests:20}") int maxRequests,
            @Value("${monitoring.alerts.comments.window-seconds:60}") long windowSeconds,
            @Value("${monitoring.alerts.comments.max-tracked-users:10000}") int maxTrackedUsers) {
        this.maxRequests = Math.max(1, maxRequests);
        this.window = Duration.ofSeconds(Math.max(1, windowSeconds));
        this.maxTrackedUsers = Math.max(1, maxTrackedUsers);
    }

    /**
     * @return seconds to wait when the caller is over the limit, or {@code 0} when the request
     *         may proceed
     */
    public long tryAcquire(Long userId, Instant now) {
        if (userId == null) {
            return 0;
        }

        Window current = windows.get(userId);
        if (current == null) {
            synchronized (registrationLock) {
                current = windows.get(userId);
                if (current == null) {
                    evictExpired(now);
                    if (windows.size() >= maxTrackedUsers) {
                        return Math.max(1, window.getSeconds());
                    }
                    current = new Window(now);
                    windows.put(userId, current);
                }
            }
        }

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

    private void evictExpired(Instant now) {
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
