package com.monitoring.alerts.entity;

/**
 * What a {@link MaintenanceWindow} mutes.
 *
 * <p>{@link #GLOBAL} mutes every alert in the organization, {@link #APPLICATION} mutes every rule
 * that targets one application, and {@link #SPECIFIC_RULE} mutes a single alert rule.
 */
public enum MaintenanceScopeType {
    GLOBAL,
    APPLICATION,
    SPECIFIC_RULE
}
