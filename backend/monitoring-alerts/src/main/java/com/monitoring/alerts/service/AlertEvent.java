package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;

import java.time.Instant;

/**
 * A state transition produced by alert evaluation: an incident started firing or was resolved.
 *
 * <p>It carries everything needed to deliver a webhook outside the evaluation transaction, so
 * the network call never runs while a database transaction is open.
 *
 * @param observedValue the metric value that caused the transition, or {@code null} for
 *                      health-check rules
 */
public record AlertEvent(
        Long incidentId,
        Long ruleId,
        String ruleName,
        Long ownerId,
        Long applicationId,
        AlertRuleType type,
        String target,
        AlertCondition condition,
        double threshold,
        Double observedValue,
        IncidentStatus status,
        Instant timestamp) {
}
