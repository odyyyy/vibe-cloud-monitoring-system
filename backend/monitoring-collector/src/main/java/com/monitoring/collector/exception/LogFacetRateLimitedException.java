package com.monitoring.collector.exception;

/**
 * The per-organization facet budget was exhausted; the client may retry after the delay.
 */
public class LogFacetRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LogFacetRateLimitedException(long retryAfterSeconds) {
        super("Too many facet requests; retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
