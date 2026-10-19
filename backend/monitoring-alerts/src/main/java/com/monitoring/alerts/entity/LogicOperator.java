package com.monitoring.alerts.entity;

/**
 * How multiple conditions of a rule are combined.
 *
 * <p>{@link #AND} fires only when every condition is breaching; {@link #OR} fires when at least
 * one is. Names are persisted verbatim, so they must not be renamed without a migration.
 */
public enum LogicOperator {
    AND,
    OR
}
