package com.monitoring.alerts.entity;

/**
 * Lifecycle state of an alert incident.
 *
 * <p>{@link #FIRING} means the rule is currently breaching for at least its configured
 * duration; {@link #RESOLVED} means the breach has cleared (or the rule was deactivated).
 */
public enum IncidentStatus {
    FIRING,
    RESOLVED
}
