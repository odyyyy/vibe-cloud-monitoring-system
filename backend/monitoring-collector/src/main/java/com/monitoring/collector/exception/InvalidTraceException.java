package com.monitoring.collector.exception;

/**
 * Raised when an ingestion batch violates a trace-level constraint (for example a single
 * trace carrying more spans than the allowed maximum). Mapped to HTTP 400.
 */
public class InvalidTraceException extends RuntimeException {

    public InvalidTraceException(String message) {
        super(message);
    }
}
