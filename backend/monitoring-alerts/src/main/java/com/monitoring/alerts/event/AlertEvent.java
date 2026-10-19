package com.monitoring.alerts.event;

import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;

import java.time.Instant;
import java.util.List;

/**
 * A state transition produced by alert evaluation: an incident started firing or was resolved.
 *
 * <p>It carries everything needed to deliver a notification outside the evaluation transaction,
 * so the network call never runs while a database transaction is open.
 *
 * @param condition  the primary (first) condition of the rule, kept for backward-compatible
 *                   payloads
 * @param threshold  the primary condition threshold
 * @param observedValue the metric value that caused the transition, or {@code null} for
 *                      health-check rules
 * @param conditions per-condition observations that produced the transition
 */
public record AlertEvent(
        Long incidentId,
        Long ruleId,
        String ruleName,
        Long organizationId,
        Long applicationId,
        AlertRuleType type,
        String target,
        AlertCondition condition,
        double threshold,
        Double observedValue,
        List<ConditionObservation> conditions,
        LogicOperator logicOperator,
        IncidentStatus status,
        Instant timestamp) {

    /**
     * A single evaluated condition at transition time.
     *
     * @param observedValue the observed metric value, or {@code null} for health-check conditions
     */
    public record ConditionObservation(
            String target,
            AlertCondition operator,
            double threshold,
            Double observedValue,
            boolean breaching) {
    }
}
