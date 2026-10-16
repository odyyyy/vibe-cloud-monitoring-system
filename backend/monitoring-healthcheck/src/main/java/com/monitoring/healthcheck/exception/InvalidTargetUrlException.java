package com.monitoring.healthcheck.exception;

/**
 * Raised when a configured target URL is not allowed (wrong scheme, credentials embedded,
 * or a loopback/private/link-local destination blocked for SSRF reasons).
 */
public class InvalidTargetUrlException extends RuntimeException {

    public InvalidTargetUrlException(String message) {
        super(message);
    }
}
