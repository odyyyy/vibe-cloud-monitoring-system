package com.monitoring.alerts.entity;

/**
 * Lifecycle state of an alert incident.
 *
 * <p>{@link #FIRING} means the rule is currently breaching for at least its configured
 * duration. {@link #ACKNOWLEDGED} means a user has taken ownership of the incident while the
 * breach is still active. {@link #RESOLVED} means the breach has cleared (or the rule was
 * deactivated, or a user closed the incident manually).
 */
public enum IncidentStatus {
    FIRING,
    ACKNOWLEDGED,
    RESOLVED
}
