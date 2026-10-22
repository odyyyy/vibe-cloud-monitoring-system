package com.monitoring.alerts.entity;

/**
 * Kind of signal an alert rule evaluates.
 *
 * <p>{@link #METRIC_THRESHOLD} compares a recent metric value against a threshold;
 * {@link #HEALTH_CHECK} watches the latest result of a synthetic health check;
 * {@link #LOG} counts matching log events in a time window and compares that count against a
 * threshold.
 */
public enum AlertRuleType {
    METRIC_THRESHOLD,
    HEALTH_CHECK,
    LOG
}
