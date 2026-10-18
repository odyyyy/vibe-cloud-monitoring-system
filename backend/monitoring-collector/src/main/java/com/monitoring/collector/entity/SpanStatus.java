package com.monitoring.collector.entity;

/**
 * Outcome of a trace span.
 *
 * <p>The set is intentionally small and fixed: it is part of the public API contract
 * (filtering, highlighting and display) and must not be extended silently.
 */
public enum SpanStatus {
    OK,
    ERROR
}
