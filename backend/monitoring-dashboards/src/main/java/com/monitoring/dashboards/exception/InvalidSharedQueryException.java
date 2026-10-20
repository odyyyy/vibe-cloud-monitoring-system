package com.monitoring.dashboards.exception;

/**
 * Raised when a public shared-dashboard query is not valid: an unusable time range or a
 * widget whose stored configuration cannot be interpreted.
 */
public class InvalidSharedQueryException extends RuntimeException {

    public InvalidSharedQueryException(String message) {
        super(message);
    }
}
