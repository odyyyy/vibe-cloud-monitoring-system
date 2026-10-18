package com.monitoring.collector.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestionRateLimiterTest {

    private MeterRegistry registry;
    private IngestionRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        limiter = new IngestionRateLimiter(provider);
    }

    @Test
    void allowsRequestsUpToLimit_thenRejects() {
        Instant now = Instant.ofEpochSecond(100 * 60 + 10);

        assertTrue(limiter.tryAcquire("7:metrics", 3, now).allowed());
        assertTrue(limiter.tryAcquire("7:metrics", 3, now).allowed());
        assertTrue(limiter.tryAcquire("7:metrics", 3, now).allowed());

        RateLimitDecision rejected = limiter.tryAcquire("7:metrics", 3, now);
        assertFalse(rejected.allowed());
        assertEquals(0, rejected.remaining());
        assertEquals(3, rejected.limit());
    }

    @Test
    void remainingTracksBudgetWithinWindow() {
        Instant now = Instant.ofEpochSecond(100 * 60 + 10);

        assertEquals(2, limiter.tryAcquire("1:metrics", 3, now).remaining());
        assertEquals(1, limiter.tryAcquire("1:metrics", 3, now).remaining());
        assertEquals(0, limiter.tryAcquire("1:metrics", 3, now).remaining());
    }

    @Test
    void rejectionReportsSecondsUntilWindowEnd() {
        // 25 seconds into the minute -> 35 seconds until the window resets.
        Instant now = Instant.ofEpochSecond(100 * 60 + 25);

        limiter.tryAcquire("1:metrics", 1, now);
        RateLimitDecision rejected = limiter.tryAcquire("1:metrics", 1, now);

        assertFalse(rejected.allowed());
        assertEquals(35, rejected.retryAfterSeconds());
    }

    @Test
    void windowResetsAtTheMinuteBoundary() {
        Instant endOfWindow = Instant.ofEpochSecond(100 * 60 + 59);
        Instant nextWindow = Instant.ofEpochSecond(101 * 60);

        assertTrue(limiter.tryAcquire("1:metrics", 1, endOfWindow).allowed());
        assertFalse(limiter.tryAcquire("1:metrics", 1, endOfWindow).allowed());

        // The very next second starts a fresh window and the full budget is available again.
        RateLimitDecision afterReset = limiter.tryAcquire("1:metrics", 1, nextWindow);
        assertTrue(afterReset.allowed());
        assertEquals(0, afterReset.remaining());
    }

    @Test
    void bucketsAreIndependentPerKey() {
        Instant now = Instant.ofEpochSecond(100 * 60 + 10);

        assertTrue(limiter.tryAcquire("7:metrics", 1, now).allowed());
        assertFalse(limiter.tryAcquire("7:metrics", 1, now).allowed());

        // A different application (and a different endpoint category) has its own budget.
        assertTrue(limiter.tryAcquire("8:metrics", 1, now).allowed());
        assertTrue(limiter.tryAcquire("7:logs", 1, now).allowed());
    }

    @Test
    void recordsRejectionMetricTaggedByApplicationId() {
        limiter.recordRejection(7L);
        limiter.recordRejection(7L);
        limiter.recordRejection(8L);

        assertEquals(2.0, registry.get(IngestionRateLimiter.RATE_LIMITED_METRIC)
                .tag(IngestionRateLimiter.APPLICATION_ID_TAG, "7").counter().count());
        assertEquals(1.0, registry.get(IngestionRateLimiter.RATE_LIMITED_METRIC)
                .tag(IngestionRateLimiter.APPLICATION_ID_TAG, "8").counter().count());
    }

    @Test
    @SuppressWarnings("unchecked")
    void worksWithoutARegistry() {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        IngestionRateLimiter noRegistry = new IngestionRateLimiter(provider);

        assertTrue(noRegistry.tryAcquire("1:metrics", 1, Instant.now()).allowed());
        noRegistry.recordRejection(1L);
    }
}
