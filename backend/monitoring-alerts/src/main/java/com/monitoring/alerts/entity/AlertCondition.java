package com.monitoring.alerts.entity;

/**
 * Comparison applied to an observed value.
 *
 * <p>{@link #GREATER_THAN} is {@code >}, {@link #LESS_THAN} is {@code <} and {@link #EQUAL}
 * is {@code ==}. Names are persisted verbatim, so they must not be renamed without a migration.
 */
public enum AlertCondition {
    GREATER_THAN,
    LESS_THAN,
    EQUAL
}
