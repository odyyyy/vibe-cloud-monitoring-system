package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.event.AlertEvent;

import java.time.Instant;

/**
 * JSON body posted to notification webhooks. It is a stable, self-contained description of the
 * alert transition. Field values originate from the platform (not from untrusted telemetry) and
 * are bounded by the entity constraints.
 */
public record AlertNotificationPayload(
        Long incidentId,
        Long ruleId,
        String ruleName,
        Long applicationId,
        AlertRuleType ruleType,
        String target,
        AlertCondition condition,
        double threshold,
        Double observedValue,
        IncidentStatus status,
        Instant timestamp) {

    public static AlertNotificationPayload from(AlertEvent event) {
        return new AlertNotificationPayload(
                event.incidentId(),
                event.ruleId(),
                event.ruleName(),
                event.applicationId(),
                event.type(),
                event.target(),
                event.condition(),
                event.threshold(),
                event.observedValue(),
                event.status(),
                event.timestamp());
    }
}
