package com.monitoring.auth.exception;

/**
 * Raised when too many failed sign-in attempts were made for one identifier/IP pair and the
 * caller is temporarily locked out.
 *
 * <p>Carries the remaining lockout in seconds so the API can answer with HTTP 429 and a
 * {@code Retry-After} header. The message is deliberately generic and is returned for any
 * identifier, existing or not, so the response never reveals whether an account exists.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final int retryAfterSeconds;

    public TooManyLoginAttemptsException(int retryAfterSeconds) {
        super("Too many failed sign-in attempts. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
