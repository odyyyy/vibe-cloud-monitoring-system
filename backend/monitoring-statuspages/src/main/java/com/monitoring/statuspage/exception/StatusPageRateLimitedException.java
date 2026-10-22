package com.monitoring.statuspage.exception;

public class StatusPageRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public StatusPageRateLimitedException(long retryAfterSeconds) {
        super("Too many requests; retry in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
