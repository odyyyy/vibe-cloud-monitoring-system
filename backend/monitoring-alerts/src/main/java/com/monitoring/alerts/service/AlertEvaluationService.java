package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.collector.service.MetricQueryService;
import com.monitoring.healthcheck.dto.HealthCheckResultResponse;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.exception.HealthCheckAccessDeniedException;
import com.monitoring.healthcheck.exception.HealthCheckNotFoundException;
import com.monitoring.healthcheck.service.HealthCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Evaluates a single alert rule and maintains its incident state.
 *
 * <p>Each evaluation is one short transaction: reads (latest metric value or latest health-check
 * result) and the resulting incident insert/update. No network calls happen here — webhook
 * delivery is queued after the transaction commits (see {@link WebhookDispatcher}).
 *
 * <h2>Missing data</h2>
 * When there is no recent data (no metric sample in the lookback window, no health result, or a
 * stale result) the evaluation returns empty and leaves the rule's state untouched. Missing data
 * is never treated as a breach or as recovery.
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

    private final AlertRuleRepository ruleRepository;
    private final AlertIncidentRepository incidentRepository;
    private final MetricQueryService metricQueryService;
    private final HealthCheckService healthCheckService;
    private final long metricLookbackMinutes;
    private final long healthFreshnessMinutes;

    public AlertEvaluationService(
            AlertRuleRepository ruleRepository,
            AlertIncidentRepository incidentRepository,
            MetricQueryService metricQueryService,
            HealthCheckService healthCheckService,
            @Value("${monitoring.alerts.metric-lookback-minutes:5}") long metricLookbackMinutes,
            @Value("${monitoring.alerts.health-freshness-minutes:10}") long healthFreshnessMinutes) {
        this.ruleRepository = ruleRepository;
        this.incidentRepository = incidentRepository;
        this.metricQueryService = metricQueryService;
        this.healthCheckService = healthCheckService;
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

        return observation.get().breaching()
                ? handleBreach(rule, observation.get().value(), now)
                : handleClear(rule, observation.get().value(), now);
    }

    private Optional<AlertEvent> handleBreach(AlertRule rule, Double value, Instant now) {
        if (rule.getBreachStartedAt() == null) {
            rule.setBreachStartedAt(now);
            ruleRepository.save(rule);
            return Optional.empty();
        }

        Instant dueAt = rule.getBreachStartedAt().plusSeconds(rule.getDurationMinutes() * 60L);
        if (now.isBefore(dueAt)) {
            return Optional.empty();
        }

        if (incidentRepository
                .findFirstByRule_IdAndStatusOrderByTriggeredAtDesc(rule.getId(), IncidentStatus.FIRING)
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
        return Optional.of(toEvent(rule, incident, value, now));
    }

    private Optional<AlertEvent> handleClear(AlertRule rule, Double value, Instant now) {
        boolean hadBreach = rule.getBreachStartedAt() != null;
        if (hadBreach) {
            rule.setBreachStartedAt(null);
            ruleRepository.save(rule);
        }

        Optional<AlertIncident> open = incidentRepository
                .findFirstByRule_IdAndStatusOrderByTriggeredAtDesc(rule.getId(), IncidentStatus.FIRING);
        if (open.isEmpty()) {
            return Optional.empty();
        }

        AlertIncident incident = open.get();
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(now);
        incidentRepository.save(incident);

        log.info("Alert rule {} ({}) resolved", rule.getId(), rule.getName());
        return Optional.of(toEvent(rule, incident, value, now));
    }

    private Optional<Observation> observe(AlertRule rule, Instant now) {
        return rule.getType() == AlertRuleType.METRIC_THRESHOLD
                ? observeMetric(rule, now)
                : observeHealthCheck(rule, now);
    }

    private Optional<Observation> observeMetric(AlertRule rule, Instant now) {
        if (rule.getApplicationId() == null) {
            return Optional.empty();
        }
        Instant since = now.minusSeconds(metricLookbackMinutes * 60L);
        OptionalDouble latest;
        try {
            latest = metricQueryService.latestValue(
                    rule.getOrganizationId(), rule.getApplicationId(), rule.getTarget(), since);
        } catch (RuntimeException ex) {
            // The application may have been deleted or become inaccessible; skip this round.
            log.debug("Metric lookup for rule {} failed: {}", rule.getId(), ex.getMessage());
            return Optional.empty();
        }
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        double value = latest.getAsDouble();
        return Optional.of(new Observation(compare(value, rule.getCondition(), rule.getThreshold()), value));
    }

    private Optional<Observation> observeHealthCheck(AlertRule rule, Instant now) {
        long healthCheckId;
        try {
            healthCheckId = Long.parseLong(rule.getTarget());
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
        return Optional.of(new Observation(breaching, null));
    }

    private boolean compare(double value, AlertCondition condition, double threshold) {
        return switch (condition) {
            case GREATER_THAN -> value > threshold;
            case LESS_THAN -> value < threshold;
            case EQUAL -> Math.abs(value - threshold) < EQUALITY_EPSILON;
        };
    }

    private AlertEvent toEvent(AlertRule rule, AlertIncident incident, Double value, Instant now) {
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
                value,
                incident.getStatus(),
                now);
    }

    /** Result of observing a rule; {@code value} is null for health-check rules. */
    private record Observation(boolean breaching, Double value) {
    }
}
