package com.monitoring.alerts.exception;

/** The organization exceeded the log-count preview budget for the current window (HTTP 429). */
public class AlertPreviewRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AlertPreviewRateLimitedException(long retryAfterSeconds) {
        super("Too many log preview requests; retry in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
