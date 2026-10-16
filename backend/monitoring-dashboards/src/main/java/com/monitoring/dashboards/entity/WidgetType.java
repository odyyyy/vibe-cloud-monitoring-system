package com.monitoring.dashboards.entity;

/**
 * Supported dashboard widget renderers. Part of the public API contract: unknown values are
 * rejected with HTTP 400 rather than stored.
 */
public enum WidgetType {
    METRIC_CHART,
    LOG_TABLE,
    STATUS
}
