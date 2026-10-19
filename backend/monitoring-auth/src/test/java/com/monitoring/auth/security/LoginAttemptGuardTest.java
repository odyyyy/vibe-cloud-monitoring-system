package com.monitoring.auth.security;

import com.monitoring.auth.config.AuthProperties;
import com.monitoring.auth.exception.TooManyLoginAttemptsException;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptGuardTest {

    private static final String KEY = "ada|203.0.113.7";

    private AuthProperties properties;
    private LoginAttemptGuard guard;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        guard = new LoginAttemptGuard(properties, noRegistry());
    }

    // --- key building ---

    @Test
    void keyNormalisesIdentifierAndCombinesWithIp() {
        assertThat(LoginAttemptGuard.key("  Ada  ", "203.0.113.7")).isEqualTo("ada|203.0.113.7");
    }

    @Test
    void keyFallsBackToUnknownIp() {
        assertThat(LoginAttemptGuard.key("ada", null)).isEqualTo("ada|unknown");
        assertThat(LoginAttemptGuard.key("ada", "  ")).isEqualTo("ada|unknown");
    }

    @Test
    void keyIsBounded() {
        assertThat(LoginAttemptGuard.key("a".repeat(500), "ip")).hasSize(320);
    }

    // --- lockout ---

    @Test
    void allowsAttemptsBelowThreshold() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 4; i++) {
            guard.recordFailure(KEY, now);
            guard.ensureNotLocked(KEY, now);
        }
    }

    @Test
    void locksAfterThresholdWithRetryAfter() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            guard.recordFailure(KEY, now);
        }

        assertThatThrownBy(() -> guard.ensureNotLocked(KEY, now))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(60);
    }

    @Test
    void retryAfterShrinksAsLockoutElapses() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            guard.recordFailure(KEY, now);
        }

        Instant halfway = now.plusSeconds(30);
        assertThatThrownBy(() -> guard.ensureNotLocked(KEY, halfway))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(30);
    }

    @Test
    void lockoutAllowsAgainAfterItExpires() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            guard.recordFailure(KEY, now);
        }

        guard.ensureNotLocked(KEY, now.plus(Duration.ofMinutes(2)));
    }

    @Test
    void consecutiveLockoutsBackOffExponentiallyAndCap() {
        properties.getLoginProtection().setMaxAttempts(1);
        properties.getLoginProtection().setFailureWindow(Duration.ofHours(1));
        properties.getLoginProtection().setLockoutBase(Duration.ofMinutes(1));
        properties.getLoginProtection().setLockoutMax(Duration.ofMinutes(5));

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        guard.recordFailure(KEY, t0);

        Instant t1 = t0.plus(Duration.ofMinutes(2));
        guard.recordFailure(KEY, t1);

        Instant t2 = t1.plus(Duration.ofMinutes(3));
        guard.recordFailure(KEY, t2);

        Instant t3 = t2.plus(Duration.ofMinutes(5));
        guard.recordFailure(KEY, t3);

        assertThatThrownBy(() -> guard.ensureNotLocked(KEY, t3))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(300);
    }

    @Test
    void successfulLoginClearsKey() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            guard.recordFailure(KEY, now);
        }

        guard.recordSuccess(KEY);

        guard.ensureNotLocked(KEY, now);
        assertThat(guard.trackedKeys()).isZero();
    }

    // --- isolation and bounding ---

    @Test
    void lockoutIsScopedToIdentifierAndIp() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String otherIp = LoginAttemptGuard.key("ada", "198.51.100.9");
        String otherId = LoginAttemptGuard.key("grace", "203.0.113.7");
        for (int i = 0; i < 5; i++) {
            guard.recordFailure(KEY, now);
        }

        guard.ensureNotLocked(otherIp, now);
        guard.ensureNotLocked(otherId, now);
    }

    @Test
    void staleKeysAreEvicted() {
        properties.getLoginProtection().setFailureWindow(Duration.ofMinutes(1));
        properties.getLoginProtection().setLockoutBase(Duration.ofMinutes(1));
        guard.recordFailure(KEY, Instant.now().minus(Duration.ofHours(1)));

        assertThat(guard.trackedKeys()).isEqualTo(1);

        guard.evictStaleWindows();

        assertThat(guard.trackedKeys()).isZero();
    }

    @Test
    void newKeysAreNotTrackedAtCapacity() {
        properties.getLoginProtection().setMaxTrackedKeys(1);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        guard.recordFailure(KEY, now);
        guard.recordFailure(LoginAttemptGuard.key("grace", "203.0.113.7"), now);

        assertThat(guard.trackedKeys()).isEqualTo(1);
    }

    // --- disabled ---

    @Test
    void disabledGuardNeverLocksOrTracks() {
        properties.getLoginProtection().setEnabled(false);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        for (int i = 0; i < 50; i++) {
            guard.recordFailure(KEY, now);
        }

        guard.ensureNotLocked(KEY, now);
        assertThat(guard.trackedKeys()).isZero();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> noRegistry() {
        return Mockito.mock(ObjectProvider.class);
    }
}
