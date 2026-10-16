package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.entity.HealthStatus;

/**
 * Result of executing a single check. {@code errorMessage} is untrusted text and is length
 * bounded before it is persisted.
 */
public record HealthCheckOutcome(HealthStatus status, int responseTimeMs, String errorMessage) {
}
