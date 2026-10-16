package com.monitoring.collector.entity;

/**
 * Severity of a structured log event.
 *
 * <p>The set is intentionally small and fixed: it is part of the public API contract
 * (filtering and display) and must not be extended silently.
 */
public enum LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}
