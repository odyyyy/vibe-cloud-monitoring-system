package com.monitoring.retention.service;

/** Retention windows (days) actually applied to an organization's telemetry. */
public record EffectiveRetention(
        int logsDays,
        int metricsDays,
        int tracesDays
) {
}
