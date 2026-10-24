package com.monitoring.alerts.exception;

/** The user exceeded the comment creation budget for the current window (HTTP 429). */
public class IncidentCommentRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public IncidentCommentRateLimitedException(long retryAfterSeconds) {
        super("Too many comments; retry in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
