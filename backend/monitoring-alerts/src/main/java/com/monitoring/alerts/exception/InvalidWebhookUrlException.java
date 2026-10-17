package com.monitoring.alerts.exception;

/**
 * Raised when a webhook URL is not allowed (not https, contains credentials, or resolves to a
 * non-public address). Mapped to HTTP 400.
 */
public class InvalidWebhookUrlException extends RuntimeException {

    public InvalidWebhookUrlException(String message) {
        super(message);
    }
}
