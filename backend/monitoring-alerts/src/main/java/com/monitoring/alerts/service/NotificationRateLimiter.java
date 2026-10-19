package com.monitoring.alerts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounds how often a single rule may notify a single channel.
 *
 * <p>Alert evaluation already emits at most one event per firing/resolution transition, but a rule
 * that flaps (fires, resolves and fires again) could otherwise flood a mailbox or webhook. The
 * limiter allows at most one notification per key (rule and channel) within a configurable window
 * (default 15 minutes), dropping the excess.
 *
 * <p>State is kept in memory and pruned opportunistically to stay bounded: the map is keyed by a
 * small composite string, entries older than the window are dropped, and the whole map is cleared
 * if it ever grows past a hard cap. This is deliberately process-local; the trade-off is that a
 * horizontally scaled deployment allows up to one notification per instance per window, which is
 * acceptable for spam prevention.
 */
@Component
public class NotificationRateLimiter {

    private static final int MAX_ENTRIES = 10_000;

    private final long minIntervalSeconds;
    private final ConcurrentHashMap<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    public NotificationRateLimiter(
            @Value("${monitoring.alerts.notifications.min-interval-seconds:900}") long minIntervalSeconds) {
        this.minIntervalSeconds = minIntervalSeconds;
    }

    /**
     * Attempts to reserve a notification slot for {@code key}.
     *
     * @return {@code true} if the caller may send, {@code false} if it is inside the cooldown
     */
    public boolean tryAcquire(String key, Instant now) {
        if (minIntervalSeconds <= 0) {
            return true;
        }
        Instant threshold = now.minusSeconds(minIntervalSeconds);
        if (lastSentAt.size() > MAX_ENTRIES) {
            lastSentAt.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
            if (lastSentAt.size() > MAX_ENTRIES) {
                lastSentAt.clear();
            }
        }

        AtomicBoolean allowed = new AtomicBoolean(false);
        lastSentAt.compute(key, (ignored, previous) -> {
            if (previous == null || !previous.isAfter(threshold)) {
                allowed.set(true);
                return now;
            }
            return previous;
        });
        return allowed.get();
    }
}
