package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.collector.entity.LogLevel;

import java.time.Instant;
import java.util.List;

/**
 * JSON body posted to notification webhooks. It is a stable, self-contained description of the
 * alert transition. Field values originate from the platform (not from untrusted telemetry) and
 * are bounded by the entity constraints.
 *
 * <p>{@code condition}/{@code threshold} describe the primary (first) condition of the rule;
 * {@code conditions} carries every evaluated condition for compound rules.
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
        List<ConditionSnapshot> conditions,
        LogicOperator logicOperator,
        IncidentStatus status,
        Instant timestamp,
        LogAlertSnapshot logDetails) {

    /**
     * Log-rule filters and the deep link. Matching log messages are intentionally not included.
     */
    public record LogAlertSnapshot(
            LogLevel level,
            String query,
            int windowMinutes,
            AlertCondition comparison,
            long countThreshold,
            long observedCount,
            String applicationName,
            String explorerUrl) {
    }

    /** Serialized per-condition view of an evaluation. */
    public record ConditionSnapshot(
            String target,
            AlertCondition operator,
            double threshold,
            Double observedValue,
            boolean breaching) {
    }

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
                event.conditions().stream()
                        .map(condition -> new ConditionSnapshot(
                                condition.target(),
                                condition.operator(),
                                condition.threshold(),
                                condition.observedValue(),
                                condition.breaching()))
                        .toList(),
                event.logicOperator(),
                event.status(),
                event.timestamp(),
                event.logDetails() == null ? null : new LogAlertSnapshot(
                        event.logDetails().level(),
                        event.logDetails().query(),
                        event.logDetails().windowMinutes(),
                        event.logDetails().comparison(),
                        event.logDetails().countThreshold(),
                        event.logDetails().observedCount(),
                        event.logDetails().applicationName(),
                        event.logDetails().explorerUrl()));
    }
}
