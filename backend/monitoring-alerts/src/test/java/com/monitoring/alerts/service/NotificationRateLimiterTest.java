package com.monitoring.alerts.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    void firstNotificationIsAllowedAndRepeatWithinWindowIsBlocked() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(900);

        assertTrue(limiter.tryAcquire("1:10", NOW));
        assertFalse(limiter.tryAcquire("1:10", NOW.plusSeconds(60)));
        assertFalse(limiter.tryAcquire("1:10", NOW.plusSeconds(899)));
    }

    @Test
    void notificationIsAllowedAfterWindowElapsed() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(900);

        assertTrue(limiter.tryAcquire("1:10", NOW));
        assertTrue(limiter.tryAcquire("1:10", NOW.plusSeconds(900)));
    }

    @Test
    void differentKeysAreIndependent() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(900);

        assertTrue(limiter.tryAcquire("1:10", NOW));
        assertTrue(limiter.tryAcquire("1:11", NOW));
        assertTrue(limiter.tryAcquire("2:10", NOW));
    }

    @Test
    void zeroIntervalDisablesLimiting() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(0);

        assertTrue(limiter.tryAcquire("1:10", NOW));
        assertTrue(limiter.tryAcquire("1:10", NOW));
    }
}
