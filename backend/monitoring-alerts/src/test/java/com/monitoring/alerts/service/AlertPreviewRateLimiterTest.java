package com.monitoring.alerts.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertPreviewRateLimiterTest {

    private static final Instant START = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void allowsUpToTheLimitThenReportsRetryDelay() {
        AlertPreviewRateLimiter limiter = new AlertPreviewRateLimiter(3, 60);

        assertEquals(0, limiter.tryAcquire(1L, START));
        assertEquals(0, limiter.tryAcquire(1L, START.plusSeconds(10)));
        assertEquals(0, limiter.tryAcquire(1L, START.plusSeconds(20)));

        long retryAfter = limiter.tryAcquire(1L, START.plusSeconds(30));
        assertTrue(retryAfter > 0);
        assertTrue(retryAfter <= 60);
    }

    @Test
    void resetsTheWindowAfterItExpires() {
        AlertPreviewRateLimiter limiter = new AlertPreviewRateLimiter(1, 60);

        assertEquals(0, limiter.tryAcquire(1L, START));
        assertTrue(limiter.tryAcquire(1L, START.plusSeconds(59)) > 0);
        assertEquals(0, limiter.tryAcquire(1L, START.plusSeconds(60)));
    }

    @Test
    void limitsEachOrganizationIndependently() {
        AlertPreviewRateLimiter limiter = new AlertPreviewRateLimiter(1, 60);

        assertEquals(0, limiter.tryAcquire(1L, START));
        assertTrue(limiter.tryAcquire(1L, START) > 0);
        assertEquals(0, limiter.tryAcquire(2L, START));
    }

    @Test
    void treatsAnUnknownOrganizationAsUnlimited() {
        AlertPreviewRateLimiter limiter = new AlertPreviewRateLimiter(1, 60);

        assertEquals(0, limiter.tryAcquire(null, START));
        assertEquals(0, limiter.tryAcquire(null, START));
    }
}
