package com.monitoring.collector.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fixed-window rate limiter for the ingestion endpoints.
 *
 * <p>Each bucket is identified by a caller-supplied key (application id + endpoint category) and
 * counts requests inside the current 60-second window. The window boundary is derived from the
 * epoch clock ({@code floorDiv(epochSecond, 60)}), so it resets deterministically and
 * independently of when a bucket was first created. A bucket only exists in memory: the number
 * of distinct applications bounds the map size, and stale buckets are evicted periodically.
 *
 * <p>The implementation favours throughput over strict fairness: the common path is a single
 * {@link AtomicInteger} increment, and only the once-per-window reset takes a short lock. This
 * keeps the limiter from becoming a bottleneck. The counter is optionally published to a
 * {@link MeterRegistry}; when no registry is present (for example in module-level tests) metrics
 * are simply skipped.
 */
@Component
public class IngestionRateLimiter {

    /** Published as {@code ingestion.rate_limited_requests_total}, tagged by applicationId. */
    static final String RATE_LIMITED_METRIC = "ingestion.rate_limited_requests_total";

    static final String APPLICATION_ID_TAG = "applicationId";

    private static final long WINDOW_SECONDS = 60L;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public IngestionRateLimiter(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    /**
     * Counts one request against the bucket and decides whether it is allowed.
     *
     * @param key   bucket identifier (for example {@code "7:metrics"})
     * @param limit maximum requests allowed in the window
     * @param now   current instant (injected for testability)
     */
    public RateLimitDecision tryAcquire(String key, int limit, Instant now) {
        long windowMinute = windowMinute(now);
        Window window = windows.computeIfAbsent(key, k -> new Window(windowMinute));
        int count = window.increment(windowMinute);

        if (count > limit) {
            return new RateLimitDecision(false, retryAfterSeconds(now), limit, 0);
        }
        return new RateLimitDecision(true, 0, limit, Math.max(0, limit - count));
    }

    /** Records one rejected request. Never logs or stores the raw API key. */
    public void recordRejection(Long applicationId) {
        if (meterRegistry != null) {
            meterRegistry.counter(RATE_LIMITED_METRIC, APPLICATION_ID_TAG, String.valueOf(applicationId))
                    .increment();
        }
    }

    /** Evicts buckets from previous windows so the map cannot grow without bound. */
    @Scheduled(fixedDelay = 60_000L)
    void evictStaleWindows() {
        long currentMinute = windowMinute(Instant.now());
        windows.entrySet().removeIf(entry -> entry.getValue().isStale(currentMinute));
    }

    private static long windowMinute(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), WINDOW_SECONDS);
    }

    private static int retryAfterSeconds(Instant now) {
        return (int) (WINDOW_SECONDS - Math.floorMod(now.getEpochSecond(), WINDOW_SECONDS));
    }

    /** Mutable per-bucket state; a single instance handles all windows over its lifetime. */
    private static final class Window {

        private final AtomicInteger count = new AtomicInteger();
        private volatile long minute;

        Window(long minute) {
            this.minute = minute;
        }

        int increment(long currentMinute) {
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

        boolean isStale(long currentMinute) {
            return minute < currentMinute;
        }
    }
}
