package com.monitoring.collector.exception;

/**
 * Raised when a trace does not exist or is not visible to the caller.
 *
 * <p>The message never includes tenant-identifying details; it is mapped to HTTP 404 by the
 * global exception handler.
 */
public class TraceNotFoundException extends RuntimeException {

    public TraceNotFoundException(String traceId) {
        super("Trace not found: " + traceId);
    }
}
