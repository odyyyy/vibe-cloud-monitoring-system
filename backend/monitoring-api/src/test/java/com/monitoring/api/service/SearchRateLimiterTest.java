package com.monitoring.api.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void limitsEachUserIndependently() {
        SearchRateLimiter limiter = new SearchRateLimiter(2, 60);

        assertThat(limiter.tryAcquire(1L, NOW)).isZero();
        assertThat(limiter.tryAcquire(1L, NOW)).isZero();
        assertThat(limiter.tryAcquire(1L, NOW)).isPositive();
        assertThat(limiter.tryAcquire(2L, NOW)).isZero();
    }

    @Test
    void windowResetsAfterTheConfiguredDuration() {
        SearchRateLimiter limiter = new SearchRateLimiter(1, 60);

        assertThat(limiter.tryAcquire(1L, NOW)).isZero();
        assertThat(limiter.tryAcquire(1L, NOW)).isPositive();
        assertThat(limiter.tryAcquire(1L, NOW.plusSeconds(60))).isZero();
    }
}
