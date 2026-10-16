package com.monitoring.healthcheck.entity;

/**
 * Outcome of a single synthetic check run.
 *
 * <p>{@link #UNKNOWN} means no check has produced a result yet; it must never be
 * reported as healthy.
 */
public enum HealthStatus {
    UP,
    DOWN,
    TIMEOUT,
    UNKNOWN
}
