package com.monitoring.api.exception;

/** The per-user search budget was exhausted; the client may retry after the advertised delay. */
public class SearchRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public SearchRateLimitedException(long retryAfterSeconds) {
        super("Too many search requests; retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
