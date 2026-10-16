package com.monitoring.collector.dto;

/**
 * Supported aggregations for a metrics query.
 * Values map to SQL aggregate functions and are not interchangeable.
 */
public enum MetricAggregation {
    AVG,
    MAX,
    MIN,
    SUM
}
