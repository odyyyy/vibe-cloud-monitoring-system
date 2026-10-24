package com.monitoring.alerts.entity;

import java.util.Locale;

/** Alert incident transition delivered to notification channels. */
public enum NotificationEventType {
    INCIDENT_OPENED,
    INCIDENT_ACKNOWLEDGED,
    INCIDENT_RESOLVED,
    SCHEDULED_REPORT;

    public static NotificationEventType fromStatus(IncidentStatus status) {
        return switch (status) {
            case FIRING -> INCIDENT_OPENED;
            case ACKNOWLEDGED -> INCIDENT_ACKNOWLEDGED;
            case RESOLVED -> INCIDENT_RESOLVED;
        };
    }

    public String snakeCase() {
        return name().toLowerCase(Locale.ROOT);
    }
}
