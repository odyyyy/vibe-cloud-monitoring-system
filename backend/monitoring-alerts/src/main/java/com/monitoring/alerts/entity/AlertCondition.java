package com.monitoring.alerts.entity;

/**
 * Comparison applied to an observed value.
 *
 * <p>{@link #GREATER_THAN} is {@code >}, {@link #GREATER_OR_EQUAL} is {@code >=},
 * {@link #LESS_THAN} is {@code <} and {@link #EQUAL} is {@code ==}. Names are persisted verbatim,
 * so they must not be renamed without a migration. {@code EQUAL} is only meaningful for metric
 * rules; log rules support greater-than, greater-or-equal and less-than count comparisons.
 */
public enum AlertCondition {
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    EQUAL
}
