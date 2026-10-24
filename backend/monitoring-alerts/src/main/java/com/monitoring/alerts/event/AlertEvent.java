package com.monitoring.alerts.event;

import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.collector.entity.LogLevel;

import java.time.Instant;
import java.util.List;

/**
 * A state transition produced by alert evaluation: an incident started firing or was resolved.
 *
 * <p>It carries everything needed to deliver a notification outside the evaluation transaction,
 * so the network call never runs while a database transaction is open.
 *
 * @param incidentId        incident identifier; {@code null} for ad-hoc test notifications
 * @param incidentStartedAt instant the incident first started firing (not the transition time)
 * @param condition  the primary (first) condition of the rule, kept for backward-compatible
 *                   payloads
 * @param threshold  the primary condition threshold
 * @param observedValue the metric value that caused the transition, or {@code null} for
 *                      health-check rules
 * @param conditions per-condition observations that produced the transition
 * @param logDetails log-rule context for notifications, or {@code null} for metric and
 *                   health-check rules
 */
public record AlertEvent(
        Long incidentId,
        Instant incidentStartedAt,
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
        Instant timestamp,
        LogAlertDetails logDetails) {

    /**
     * Log-rule context. {@code explorerUrl} is absolute when a customer-facing frontend base URL is
     * configured and {@code null} otherwise; notifications then omit the link. The raw matching
     * log messages are deliberately not included, only the filters and the count.
     */
    public record LogAlertDetails(
            LogLevel level,
            String query,
            int windowMinutes,
            AlertCondition comparison,
            long countThreshold,
            long observedCount,
            String applicationName,
            String explorerUrl) {
    }

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
