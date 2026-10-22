package com.monitoring.retention.entity;

/**
 * Telemetry classes covered by retention. Audit events are intentionally excluded: their
 * lifecycle is managed separately and must never be touched by the purge job.
 */
public enum RetentionDataType {
    LOGS,
    METRICS,
    TRACES
}
