package com.monitoring.alerts.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentCommentRateLimiterTest {

    @Test
    void limitsEachUserSeparatelyAndResetsAfterTheWindow() {
        IncidentCommentRateLimiter limiter = new IncidentCommentRateLimiter(2, 60, 100);
        Instant now = Instant.parse("2026-10-24T10:00:00Z");

        assertEquals(0, limiter.tryAcquire(1L, now));
        assertEquals(0, limiter.tryAcquire(1L, now.plusSeconds(1)));
        assertTrue(limiter.tryAcquire(1L, now.plusSeconds(2)) > 0);
        assertEquals(0, limiter.tryAcquire(2L, now.plusSeconds(2)));

        assertEquals(0, limiter.tryAcquire(1L, now.plusSeconds(61)));
    }

    @Test
    void boundsTheNumberOfTrackedUsers() {
        IncidentCommentRateLimiter limiter = new IncidentCommentRateLimiter(2, 60, 2);
        Instant now = Instant.parse("2026-10-24T10:00:00Z");

        assertEquals(0, limiter.tryAcquire(1L, now));
        assertEquals(0, limiter.tryAcquire(2L, now));
        assertTrue(limiter.tryAcquire(3L, now) > 0);

        assertEquals(0, limiter.tryAcquire(3L, now.plusSeconds(61)));
    }
}
