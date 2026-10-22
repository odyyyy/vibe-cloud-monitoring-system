package com.monitoring.alerts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window, per-organization rate limiter for the log-count preview endpoint.
 *
 * <p>The preview runs a {@code COUNT(*)} over logs, so it is more expensive than a normal rule
 * read. Limiting per organization (rather than per user or IP) bounds the total load one tenant
 * can generate regardless of how many members it has. State is in-memory and resets on restart,
 * which is acceptable for a read-only calibration helper; it is not used for security decisions.
 */
@Component
public class AlertPreviewRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    public AlertPreviewRateLimiter(
            @Value("${monitoring.alerts.preview.max-requests:30}") int maxRequests,
            @Value("${monitoring.alerts.preview.window-seconds:60}") long windowSeconds) {
        this.maxRequests = Math.max(1, maxRequests);
        this.window = Duration.ofSeconds(Math.max(1, windowSeconds));
    }

    /**
     * @return seconds to wait when the caller is over the limit, or {@code 0} when the request
     *         may proceed
     */
    public long tryAcquire(Long organizationId, Instant now) {
        if (organizationId == null) {
            return 0;
        }
        Window current = windows.computeIfAbsent(organizationId, ignored -> new Window(now));
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

    private static final class Window {
        private Instant windowStart;
        private int count;

        private Window(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
