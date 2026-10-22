package com.monitoring.collector.service;

import com.monitoring.collector.config.LogFacetProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFacetRateLimiterTest {

    private static final Instant START = Instant.parse("2026-10-01T10:00:00Z");

    private static LogFacetRateLimiter limiter(int maxRequests, int windowSeconds) {
        LogFacetProperties properties = new LogFacetProperties();
        properties.setMaxRequests(maxRequests);
        properties.setWindowSeconds(windowSeconds);
        return new LogFacetRateLimiter(properties);
    }

    @Test
    void allowsUpToTheLimitThenReportsRetryDelay() {
        LogFacetRateLimiter limiter = limiter(3, 60);

        assertEquals(0, limiter.tryAcquire(1L, START));
        assertEquals(0, limiter.tryAcquire(1L, START.plusSeconds(10)));
        assertEquals(0, limiter.tryAcquire(1L, START.plusSeconds(20)));

        long retryAfter = limiter.tryAcquire(1L, START.plusSeconds(30));
        assertTrue(retryAfter > 0);
        assertTrue(retryAfter <= 60);
    }

    @Test
    void resetsTheWindowAfterItExpires() {
        LogFacetRateLimiter limiter = limiter(1, 60);

        assertEquals(0, limiter.tryAcquire(1L, START));
        assertTrue(limiter.tryAcquire(1L, START.plusSeconds(59)) > 0);
        assertEquals(0, limiter.tryAcquire(1L, START.plusSeconds(60)));
    }

    @Test
    void limitsEachOrganizationIndependently() {
        LogFacetRateLimiter limiter = limiter(1, 60);

        assertEquals(0, limiter.tryAcquire(1L, START));
        assertTrue(limiter.tryAcquire(1L, START) > 0);
        assertEquals(0, limiter.tryAcquire(2L, START));
    }

    @Test
    void neverReturnsAZeroRetryDelayWhenLimited() {
        LogFacetRateLimiter limiter = limiter(1, 60);

        assertEquals(0, limiter.tryAcquire(1L, START));
        // A caller blocked right before the window ends still gets a positive delay.
        assertTrue(limiter.tryAcquire(1L, START.plusSeconds(60).minusMillis(500)) > 0);
    }

    @Test
    void treatsAnUnknownOrganizationAsUnlimited() {
        LogFacetRateLimiter limiter = limiter(1, 60);

        assertEquals(0, limiter.tryAcquire(null, START));
        assertEquals(0, limiter.tryAcquire(null, START));
    }

    @Test
    void treatsMisconfiguredLimitsAsAtLeastOne() {
        LogFacetRateLimiter limiter = limiter(0, 0);

        assertEquals(0, limiter.tryAcquire(1L, START));
        assertTrue(limiter.tryAcquire(1L, START) > 0);
    }
}
