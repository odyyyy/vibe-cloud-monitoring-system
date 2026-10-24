package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.NotificationEventType;
import com.monitoring.alerts.event.AlertEvent;

import java.time.Instant;

/**
 * Stable JSON body posted to generic webhook channels.
 *
 * <p>It contains only platform-authored fields: incident identity, rule name, severity,
 * application metadata, the representative observed value/threshold, the incident start instant
 * and an optional deep link. Raw log content and channel secrets are never included.
 */
public record AlertNotificationPayload(
        NotificationEventType eventType,
        Long incidentId,
        String ruleName,
        String severity,
        Long applicationId,
        String applicationName,
        Double value,
        double threshold,
        Instant startedAt,
        String deepLinkUrl,
        boolean test) {

    public static AlertNotificationPayload from(
            AlertEvent event, String applicationName, String deepLinkUrl, boolean test) {
        return new AlertNotificationPayload(
                NotificationEventType.fromStatus(event.status()),
                event.incidentId(),
                event.ruleName(),
                severity(event),
                event.applicationId(),
                applicationName,
                event.observedValue(),
                event.threshold(),
                event.incidentStartedAt() == null ? event.timestamp() : event.incidentStartedAt(),
                deepLinkUrl,
                test);
    }

    /** Notification severity derived from the incident transition. */
    public static String severity(AlertEvent event) {
        return switch (event.status()) {
            case FIRING -> "CRITICAL";
            case ACKNOWLEDGED -> "WARNING";
            case RESOLVED -> "INFO";
        };
    }
}
