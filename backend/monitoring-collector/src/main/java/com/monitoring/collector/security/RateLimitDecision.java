package com.monitoring.collector.security;

/**
 * Outcome of a rate-limit check for one request.
 *
 * @param allowed          whether the request may proceed
 * @param retryAfterSeconds seconds until the current window ends; only meaningful when the
 *                          request was rejected
 * @param limit            the configured limit of the bucket (requests per minute)
 * @param remaining        the number of requests left in the current window
 */
public record RateLimitDecision(boolean allowed, int retryAfterSeconds, int limit, int remaining) {
}
