package com.monitoring.auth.security;

import com.monitoring.auth.config.AuthProperties;
import com.monitoring.auth.exception.TooManyLoginAttemptsException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection for password sign-in.
 *
 * <p>Failed attempts are counted per {@code identifier|clientIp} key. After
 * {@code max-attempts} failures inside {@code failure-window} the key is locked for a
 * duration that grows exponentially with each consecutive lockout ({@code lockout-base},
 * doubling up to {@code lockout-max}). A successful sign-in clears the key. A pair that has
 * been idle for a full {@code failure-window} is forgotten, so the escalation level also
 * decays once an attack stops.
 *
 * <p>Keying on identifier <em>and</em> client IP means an attacker from one address cannot
 * lock a user out globally, while a targeted (single-address) password guesser is still
 * stopped. A distributed attack that rotates source addresses and identifiers is a residual
 * risk (documented); a single shared store (for example Redis) would be required to bound it
 * across application instances.
 *
 * <p>The guard is deliberately checked <em>before</em> any user lookup, so the resulting HTTP
 * 429 is identical for existing and unknown identifiers and never leaks account existence.
 * State is bounded: stale keys are evicted periodically and the map is capped by
 * {@code max-tracked-keys}; under extreme key cardinality new keys are not tracked (fail
 * open) rather than growing without bound.
 */
@Component
public class LoginAttemptGuard {

    /** Published as {@code auth.login.blocked_total}; no identifier/IP tag to avoid cardinality. */
    static final String BLOCKED_METRIC = "auth.login.blocked_total";

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);
    private static final int MAX_KEY_LENGTH = 320;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AuthProperties.LoginProtection properties;
    private final MeterRegistry meterRegistry;

    public LoginAttemptGuard(AuthProperties authProperties,
                             ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.properties = authProperties.getLoginProtection();
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    /**
     * Builds the bucket key from the submitted identifier and the resolved client IP.
     *
     * <p>The identifier is normalised (trimmed, lower-cased) so casing and padding cannot be
     * used to get a fresh budget. A missing IP maps to a shared {@code unknown} bucket.
     */
    public static String key(String identifier, String clientIp) {
        String id = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        String raw = id + "|" + ip;
        return raw.length() <= MAX_KEY_LENGTH ? raw : raw.substring(0, MAX_KEY_LENGTH);
    }

    /** Rejects the attempt with HTTP 429 semantics while the key is locked. */
    public void ensureNotLocked(String key, Instant now) {
        if (!properties.isEnabled()) {
            return;
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.isLocked(now)) {
                throw new TooManyLoginAttemptsException(retryAfterSeconds(now, entry.lockedUntil));
            }
        }
    }

    /** Records one failed attempt, locking the key once the threshold is reached. */
    public void recordFailure(String key, Instant now) {
        if (!properties.isEnabled()) {
            return;
        }
        if (entries.size() >= properties.getMaxTrackedKeys()) {
            evictStale(now);
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() >= properties.getMaxTrackedKeys()) {
                return;
            }
            entry = entries.computeIfAbsent(key, k -> new Entry(now));
        }

        synchronized (entry) {
            if (Duration.between(entry.windowStart, now).compareTo(properties.getFailureWindow()) >= 0) {
                entry.failures = 0;
                entry.windowStart = now;
                entry.lockoutLevel = 0;
                entry.lockedUntil = null;
            }

            entry.failures++;
            if (entry.failures >= properties.getMaxAttempts()) {
                entry.lockoutLevel++;
                Duration lockout = lockoutDuration(entry.lockoutLevel);
                entry.lockedUntil = now.plus(lockout);
                entry.failures = 0;
                recordBlocked();
                log.warn("Sign-in temporarily blocked for {}s after repeated failed attempts (level={})",
                        lockout.toSeconds(), entry.lockoutLevel);
            }
        }
    }

    /** Clears the key after a successful sign-in. */
    public void recordSuccess(String key) {
        if (!properties.isEnabled()) {
            return;
        }
        entries.remove(key);
    }

    /** Evicts keys that have been idle for a full failure window so memory stays bounded. */
    @Scheduled(fixedDelay = 60_000L)
    void evictStaleWindows() {
        evictStale(Instant.now());
    }

    /** Number of tracked keys; package-private for tests to assert memory bounding. */
    int trackedKeys() {
        return entries.size();
    }

    private void evictStale(Instant now) {
        Duration retention = properties.getFailureWindow();
        entries.entrySet().removeIf(entry -> entry.getValue().isStale(now, retention));
    }

    private Duration lockoutDuration(int level) {
        Duration duration = properties.getLockoutBase();
        for (int i = 1; i < level; i++) {
            duration = duration.multipliedBy(2);
            if (duration.compareTo(properties.getLockoutMax()) >= 0) {
                return properties.getLockoutMax();
            }
        }
        return duration.compareTo(properties.getLockoutMax()) > 0
                ? properties.getLockoutMax()
                : duration;
    }

    private static int retryAfterSeconds(Instant now, Instant lockedUntil) {
        long millis = Math.max(0L, Duration.between(now, lockedUntil).toMillis());
        return (int) Math.max(1L, (millis + 999L) / 1000L);
    }

    private void recordBlocked() {
        if (meterRegistry != null) {
            meterRegistry.counter(BLOCKED_METRIC).increment();
        }
    }

    /** Mutable per-key state; a single instance survives across lockouts to escalate backoff. */
    private static final class Entry {

        private long failures;
        private Instant windowStart;
        private Instant lockedUntil;
        private int lockoutLevel;

        Entry(Instant now) {
            this.windowStart = now;
        }

        boolean isLocked(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        boolean isStale(Instant now, Duration retention) {
            Instant lastActivity = lockedUntil != null && lockedUntil.isAfter(windowStart)
                    ? lockedUntil
                    : windowStart;
            return Duration.between(lastActivity, now).compareTo(retention) >= 0;
        }
    }
}
