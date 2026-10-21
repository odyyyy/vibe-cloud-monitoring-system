package com.monitoring.alerts.service;

import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.collector.service.MetricQueryService;
import com.monitoring.healthcheck.dto.response.HealthCheckResultResponse;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.exception.HealthCheckAccessDeniedException;
import com.monitoring.healthcheck.exception.HealthCheckNotFoundException;
import com.monitoring.healthcheck.service.HealthCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Evaluates a single alert rule and maintains its incident state.
 *
 * <p>Each evaluation is one short transaction: reads (latest metric value or latest health-check
 * result) and the resulting incident insert/update. No network calls happen here — notification
 * delivery is queued after the transaction commits (see {@code AlertNotificationDispatcher}).
 *
 * <h2>Compound conditions</h2>
 * A rule may carry several conditions combined by {@link LogicOperator}. {@code AND} fires only
 * when every condition breaches; {@code OR} fires when any one does. A rule with no explicit
 * conditions falls back to its legacy single target/condition/threshold.
 *
 * <h2>Missing data</h2>
 * When any required condition has no recent data (no metric sample in the lookback window, no
 * health result, or a stale result) the whole evaluation returns empty and leaves the rule's state
 * untouched. Missing data is never treated as a breach or as recovery, which also prevents a
 * partially-observed {@code AND} rule from firing on incomplete evidence.
 *
 * <h2>Duration</h2>
 * A rule must breach continuously for {@code durationMinutes} before an incident is opened. The
 * first breaching evaluation records {@code breachStartedAt}; the incident fires only once that
 * timestamp is at least the duration in the past.
 */
@Service
@Slf4j
public class AlertEvaluationService {

    /** Absolute tolerance used for {@link AlertCondition#EQUAL} comparisons. */
    private static final double EQUALITY_EPSILON = 1e-9;

    /** Incidents that still represent an active breach (manual resolution closes them). */
    private static final List<IncidentStatus> OPEN_STATUSES =
            List.of(IncidentStatus.FIRING, IncidentStatus.ACKNOWLEDGED);

    private final AlertRuleRepository ruleRepository;
    private final AlertIncidentRepository incidentRepository;
    private final MetricQueryService metricQueryService;
    private final HealthCheckService healthCheckService;
    private final MaintenanceWindowService maintenanceWindowService;
    private final long metricLookbackMinutes;
    private final long healthFreshnessMinutes;

    public AlertEvaluationService(
            AlertRuleRepository ruleRepository,
            AlertIncidentRepository incidentRepository,
            MetricQueryService metricQueryService,
            HealthCheckService healthCheckService,
            MaintenanceWindowService maintenanceWindowService,
            @Value("${monitoring.alerts.metric-lookback-minutes:5}") long metricLookbackMinutes,
            @Value("${monitoring.alerts.health-freshness-minutes:10}") long healthFreshnessMinutes) {
        this.ruleRepository = ruleRepository;
        this.incidentRepository = incidentRepository;
        this.metricQueryService = metricQueryService;
        this.healthCheckService = healthCheckService;
        this.maintenanceWindowService = maintenanceWindowService;
        this.metricLookbackMinutes = metricLookbackMinutes;
        this.healthFreshnessMinutes = healthFreshnessMinutes;
    }

    /**
     * @return the resulting {@link AlertEvent} when an incident was opened or resolved, otherwise
     *         empty (no change, missing data, or not yet past the duration)
     */
    @Transactional
    public Optional<AlertEvent> evaluate(Long ruleId, Instant now) {
        AlertRule rule = ruleRepository.findById(ruleId).orElse(null);
        if (rule == null || !rule.isActive()) {
            return Optional.empty();
        }

        Optional<Observation> observation = observe(rule, now);
        if (observation.isEmpty()) {
            return Optional.empty();
        }

        boolean muted = maintenanceWindowService.isMuted(
                rule.getOrganizationId(), rule.getApplicationId(), rule.getId(), now);

        return observation.get().breaching()
                ? handleBreach(rule, observation.get(), now, muted)
                : handleClear(rule, observation.get(), now, muted);
    }

    private Optional<AlertEvent> handleBreach(
            AlertRule rule, Observation observation, Instant now, boolean muted) {
        if (rule.getBreachStartedAt() == null) {
            rule.setBreachStartedAt(now);
            ruleRepository.save(rule);
            return Optional.empty();
        }

        Instant dueAt = rule.getBreachStartedAt().plusSeconds(rule.getDurationMinutes() * 60L);
        if (now.isBefore(dueAt)) {
            return Optional.empty();
        }

        if (muted) {
            // The breach is real, but the user asked the platform to stay quiet during the window.
            log.info("Alert rule {} ({}) is muted by a maintenance window", rule.getId(), rule.getName());
            return Optional.empty();
        }

        if (incidentRepository
                .findFirstByRule_IdAndStatusInOrderByTriggeredAtDesc(rule.getId(), OPEN_STATUSES)
                .isPresent()) {
            return Optional.empty();
        }

        AlertIncident incident = incidentRepository.save(AlertIncident.builder()
                .rule(rule)
                .organizationId(rule.getOrganizationId())
                .status(IncidentStatus.FIRING)
                .triggeredAt(now)
                .build());

        log.info("Alert rule {} ({}) is firing", rule.getId(), rule.getName());
        return Optional.of(toEvent(rule, incident, observation, now));
    }

    private Optional<AlertEvent> handleClear(
            AlertRule rule, Observation observation, Instant now, boolean muted) {
        boolean hadBreach = rule.getBreachStartedAt() != null;
        if (hadBreach) {
            rule.setBreachStartedAt(null);
            ruleRepository.save(rule);
        }

        Optional<AlertIncident> open = incidentRepository
                .findFirstByRule_IdAndStatusInOrderByTriggeredAtDesc(rule.getId(), OPEN_STATUSES);
        if (open.isEmpty()) {
            return Optional.empty();
        }

        AlertIncident incident = open.get();
        // Automatic recovery is not attributed to a human actor.
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedBy(null);
        incident.setResolvedAt(now);
        incidentRepository.save(incident);

        if (muted) {
            // State is updated, but the recovery notification is suppressed as well.
            log.info("Alert rule {} ({}) resolved silently by a maintenance window",
                    rule.getId(), rule.getName());
            return Optional.empty();
        }

        log.info("Alert rule {} ({}) resolved", rule.getId(), rule.getName());
        return Optional.of(toEvent(rule, incident, observation, now));
    }

    private Optional<Observation> observe(AlertRule rule, Instant now) {
        List<AlertRuleCondition> conditions = effectiveConditions(rule);
        List<AlertEvent.ConditionObservation> evaluated = new ArrayList<>(conditions.size());
        List<Double> values = new ArrayList<>(conditions.size());

        for (AlertRuleCondition condition : conditions) {
            Optional<ConditionObservation> observed = observeCondition(rule, condition, now);
            if (observed.isEmpty()) {
                // Incomplete evidence: do not change the rule's state this round.
                return Optional.empty();
            }
            evaluated.add(new AlertEvent.ConditionObservation(
                    condition.getTarget(),
                    condition.getOperator(),
                    condition.getThreshold(),
                    observed.get().value(),
                    observed.get().breaching()));
            values.add(observed.get().value());
        }

        boolean breaching = combine(evaluated, rule.getLogicOperator());
        return Optional.of(new Observation(breaching, representativeValue(evaluated, values), evaluated));
    }

    private List<AlertRuleCondition> effectiveConditions(AlertRule rule) {
        if (!rule.getConditions().isEmpty()) {
            return rule.getConditions();
        }
        return List.of(AlertRuleCondition.builder()
                .target(rule.getTarget())
                .operator(rule.getCondition())
                .threshold(rule.getThreshold())
                .build());
    }

    private boolean combine(List<AlertEvent.ConditionObservation> observations, LogicOperator operator) {
        if (operator == LogicOperator.OR) {
            return observations.stream().anyMatch(AlertEvent.ConditionObservation::breaching);
        }
        return observations.stream().allMatch(AlertEvent.ConditionObservation::breaching);
    }

    /**
     * Chooses the value reported on the event: the first breaching condition when any breaches,
     * otherwise the first condition's value. Null values (health-check conditions) are skipped.
     */
    private Double representativeValue(List<AlertEvent.ConditionObservation> observations, List<Double> values) {
        for (int i = 0; i < observations.size(); i++) {
            if (observations.get(i).breaching() && values.get(i) != null) {
                return values.get(i);
            }
        }
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Optional<ConditionObservation> observeCondition(AlertRule rule, AlertRuleCondition condition, Instant now) {
        return rule.getType() == AlertRuleType.METRIC_THRESHOLD
                ? observeMetricCondition(rule, condition, now)
                : observeHealthCheckCondition(rule, condition, now);
    }

    private Optional<ConditionObservation> observeMetricCondition(
            AlertRule rule, AlertRuleCondition condition, Instant now) {
        if (rule.getApplicationId() == null) {
            return Optional.empty();
        }
        Instant since = now.minusSeconds(metricLookbackMinutes * 60L);
        OptionalDouble latest;
        try {
            latest = metricQueryService.latestValue(
                    rule.getOrganizationId(), rule.getApplicationId(), condition.getTarget(), since);
        } catch (RuntimeException ex) {
            // The application may have been deleted or become inaccessible; skip this round.
            log.debug("Metric lookup for rule {} failed: {}", rule.getId(), ex.getMessage());
            return Optional.empty();
        }
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        double value = latest.getAsDouble();
        return Optional.of(new ConditionObservation(
                compare(value, condition.getOperator(), condition.getThreshold()), value));
    }

    private Optional<ConditionObservation> observeHealthCheckCondition(
            AlertRule rule, AlertRuleCondition condition, Instant now) {
        long healthCheckId;
        try {
            healthCheckId = Long.parseLong(condition.getTarget());
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }

        List<HealthCheckResultResponse> results;
        try {
            results = healthCheckService.listResults(rule.getOrganizationId(), healthCheckId, 1);
        } catch (HealthCheckNotFoundException | HealthCheckAccessDeniedException ex) {
            log.debug("Health check {} for rule {} is gone: {}", healthCheckId, rule.getId(), ex.getMessage());
            return Optional.empty();
        }
        if (results.isEmpty()) {
            return Optional.empty();
        }

        HealthCheckResultResponse latest = results.get(0);
        Instant checkedAt = latest.getCheckedAt();
        if (checkedAt == null || checkedAt.isBefore(now.minusSeconds(healthFreshnessMinutes * 60L))) {
            // A stale result is not evidence of the current state.
            return Optional.empty();
        }

        boolean breaching = latest.getStatus() == HealthStatus.DOWN || latest.getStatus() == HealthStatus.TIMEOUT;
        return Optional.of(new ConditionObservation(breaching, null));
    }

    private boolean compare(double value, AlertCondition condition, double threshold) {
        return switch (condition) {
            case GREATER_THAN -> value > threshold;
            case LESS_THAN -> value < threshold;
            case EQUAL -> Math.abs(value - threshold) < EQUALITY_EPSILON;
        };
    }

    private AlertEvent toEvent(AlertRule rule, AlertIncident incident, Observation observation, Instant now) {
        return new AlertEvent(
                incident.getId(),
                rule.getId(),
                rule.getName(),
                rule.getOrganizationId(),
                rule.getApplicationId(),
                rule.getType(),
                rule.getTarget(),
                rule.getCondition(),
                rule.getThreshold(),
                observation.value(),
                observation.conditions(),
                rule.getLogicOperator(),
                incident.getStatus(),
                now);
    }

    /**
     * Result of observing a rule.
     *
     * @param value the representative observed metric value, or {@code null} for health-check rules
     */
    private record Observation(
            boolean breaching,
            Double value,
            List<AlertEvent.ConditionObservation> conditions) {
    }

    /** Result of observing a single condition. */
    private record ConditionObservation(boolean breaching, Double value) {
    }
}
