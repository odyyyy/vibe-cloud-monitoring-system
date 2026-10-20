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
import com.monitoring.healthcheck.service.HealthCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlertEvaluationServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long APP_ID = 7L;
    private static final String METRIC = "system.cpu.usage";
    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    private AlertRuleRepository ruleRepository;
    private AlertIncidentRepository incidentRepository;
    private MetricQueryService metricQueryService;
    private HealthCheckService healthCheckService;
    private AlertEvaluationService service;

    @BeforeEach
    void setUp() {
        ruleRepository = Mockito.mock(AlertRuleRepository.class);
        incidentRepository = Mockito.mock(AlertIncidentRepository.class);
        metricQueryService = Mockito.mock(MetricQueryService.class);
        healthCheckService = Mockito.mock(HealthCheckService.class);
        service = new AlertEvaluationService(ruleRepository, incidentRepository,
                metricQueryService, healthCheckService, 5L, 10L);
        when(incidentRepository.save(any(AlertIncident.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AlertRule metricRule() {
        return AlertRule.builder()
                .id(1L)
                .organizationId(OWNER_ID)
                .applicationId(APP_ID)
                .name("High CPU")
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target(METRIC)
                .condition(AlertCondition.GREATER_THAN)
                .threshold(90.0)
                .durationMinutes(1)
                .active(true)
                .build();
    }

    private AlertRule healthCheckRule() {
        return AlertRule.builder()
                .id(2L)
                .organizationId(OWNER_ID)
                .name("API down")
                .type(AlertRuleType.HEALTH_CHECK)
                .target("5")
                .condition(AlertCondition.EQUAL)
                .threshold(0.0)
                .durationMinutes(1)
                .active(true)
                .build();
    }

    private void latestValue(double value) {
        when(metricQueryService.latestValue(eq(OWNER_ID), eq(APP_ID), eq(METRIC), any(Instant.class)))
                .thenReturn(OptionalDouble.of(value));
    }

    private void noOpenIncident(long ruleId) {
        when(incidentRepository.findFirstByRule_IdAndStatusInOrderByTriggeredAtDesc(
                eq(ruleId), any())).thenReturn(Optional.empty());
    }

    @Test
    void firstBreachRecordsStartWithoutFiring() {
        AlertRule rule = metricRule();
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        latestValue(95.0);

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isEmpty());
        assertEquals(NOW, rule.getBreachStartedAt());
        verify(ruleRepository).save(rule);
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void breachFiresAfterDurationElapsed() {
        AlertRule rule = metricRule();
        rule.setBreachStartedAt(NOW.minusSeconds(61));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        latestValue(95.0);
        noOpenIncident(1L);

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());
        assertEquals(95.0, event.get().observedValue());
        assertEquals(1L, event.get().ruleId());
    }

    @Test
    void breachWithinDurationDoesNotFireYet() {
        AlertRule rule = metricRule();
        rule.setBreachStartedAt(NOW.minusSeconds(30));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        latestValue(95.0);

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isEmpty());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void recoveryResolvesOpenIncident() {
        AlertRule rule = metricRule();
        rule.setBreachStartedAt(NOW.minusSeconds(120));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        latestValue(10.0);

        AlertIncident firing = AlertIncident.builder()
                .id(3L)
                .rule(rule)
                .organizationId(OWNER_ID)
                .status(IncidentStatus.FIRING)
                .triggeredAt(NOW.minusSeconds(120))
                .build();
        when(incidentRepository.findFirstByRule_IdAndStatusInOrderByTriggeredAtDesc(eq(1L), any()))
                .thenReturn(Optional.of(firing));

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.RESOLVED, event.get().status());
        assertEquals(IncidentStatus.RESOLVED, firing.getStatus());
        assertEquals(NOW, firing.getResolvedAt());
        assertNull(rule.getBreachStartedAt());
    }

    @Test
    void acknowledgedIncidentIsTreatedAsOpenAndResolvedOnRecovery() {
        AlertRule rule = metricRule();
        rule.setBreachStartedAt(NOW.minusSeconds(120));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        latestValue(10.0);

        AlertIncident acknowledged = AlertIncident.builder()
                .id(5L)
                .rule(rule)
                .organizationId(OWNER_ID)
                .status(IncidentStatus.ACKNOWLEDGED)
                .triggeredAt(NOW.minusSeconds(120))
                .acknowledgedBy(42L)
                .build();
        when(incidentRepository.findFirstByRule_IdAndStatusInOrderByTriggeredAtDesc(eq(1L), any()))
                .thenReturn(Optional.of(acknowledged));

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.RESOLVED, event.get().status());
        assertEquals(IncidentStatus.RESOLVED, acknowledged.getStatus());
        assertNull(acknowledged.getResolvedBy());
    }

    @Test
    void acknowledgedIncidentPreventsDuplicateFiring() {
        AlertRule rule = metricRule();
        rule.setBreachStartedAt(NOW.minusSeconds(120));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        latestValue(95.0);

        AlertIncident acknowledged = AlertIncident.builder()
                .id(5L)
                .rule(rule)
                .organizationId(OWNER_ID)
                .status(IncidentStatus.ACKNOWLEDGED)
                .triggeredAt(NOW.minusSeconds(120))
                .build();
        when(incidentRepository.findFirstByRule_IdAndStatusInOrderByTriggeredAtDesc(eq(1L), any()))
                .thenReturn(Optional.of(acknowledged));

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isEmpty());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void missingMetricDataLeavesStateUntouched() {
        AlertRule rule = metricRule();
        Instant breachStartedAt = NOW.minusSeconds(120);
        rule.setBreachStartedAt(breachStartedAt);
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        when(metricQueryService.latestValue(eq(OWNER_ID), eq(APP_ID), eq(METRIC), any(Instant.class)))
                .thenReturn(OptionalDouble.empty());

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isEmpty());
        assertEquals(breachStartedAt, rule.getBreachStartedAt());
        verify(ruleRepository, never()).save(any());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void healthCheckDownFiresAfterDuration() {
        AlertRule rule = healthCheckRule();
        rule.setBreachStartedAt(NOW.minusSeconds(61));
        when(ruleRepository.findById(2L)).thenReturn(Optional.of(rule));
        when(healthCheckService.listResults(OWNER_ID, 5L, 1)).thenReturn(List.of(
                HealthCheckResultResponse.builder()
                        .id(1L)
                        .configId(5L)
                        .status(HealthStatus.DOWN)
                        .checkedAt(NOW)
                        .build()));
        noOpenIncident(2L);

        Optional<AlertEvent> event = service.evaluate(2L, NOW);

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());
        assertNull(event.get().observedValue());
    }

    @Test
    void staleHealthCheckResultIsIgnored() {
        AlertRule rule = healthCheckRule();
        rule.setBreachStartedAt(NOW.minusSeconds(120));
        when(ruleRepository.findById(2L)).thenReturn(Optional.of(rule));
        when(healthCheckService.listResults(OWNER_ID, 5L, 1)).thenReturn(List.of(
                HealthCheckResultResponse.builder()
                        .id(1L)
                        .configId(5L)
                        .status(HealthStatus.DOWN)
                        .checkedAt(NOW.minusSeconds(3600))
                        .build()));

        Optional<AlertEvent> event = service.evaluate(2L, NOW);

        assertTrue(event.isEmpty());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void healthCheckUpResolvesOpenIncident() {
        AlertRule rule = healthCheckRule();
        rule.setBreachStartedAt(NOW.minusSeconds(120));
        when(ruleRepository.findById(2L)).thenReturn(Optional.of(rule));
        when(healthCheckService.listResults(OWNER_ID, 5L, 1)).thenReturn(List.of(
                HealthCheckResultResponse.builder()
                        .id(1L)
                        .configId(5L)
                        .status(HealthStatus.UP)
                        .checkedAt(NOW)
                        .build()));
        AlertIncident firing = AlertIncident.builder()
                .id(4L)
                .rule(rule)
                .organizationId(OWNER_ID)
                .status(IncidentStatus.FIRING)
                .build();
        when(incidentRepository.findFirstByRule_IdAndStatusInOrderByTriggeredAtDesc(eq(2L), any()))
                .thenReturn(Optional.of(firing));

        Optional<AlertEvent> event = service.evaluate(2L, NOW);

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.RESOLVED, event.get().status());
    }

    @Test
    void inactiveRuleIsNotEvaluated() {
        AlertRule rule = metricRule();
        rule.setActive(false);
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isEmpty());
        verifyNoInteractions(metricQueryService, healthCheckService);
    }

    @Test
    void missingRuleIsNotEvaluated() {
        when(ruleRepository.findById(1L)).thenReturn(Optional.empty());

        assertTrue(service.evaluate(1L, NOW).isEmpty());
        verifyNoInteractions(metricQueryService, healthCheckService);
    }

    @Test
    void equalityConditionUsesTolerance() {
        AlertRule rule = metricRule();
        rule.setCondition(AlertCondition.EQUAL);
        rule.setThreshold(50.0);
        rule.setBreachStartedAt(NOW.minusSeconds(61));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        latestValue(50.0);
        noOpenIncident(1L);

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());
    }

    private AlertRule compoundRule(LogicOperator operator) {
        return AlertRule.builder()
                .id(1L)
                .organizationId(OWNER_ID)
                .applicationId(APP_ID)
                .name("CPU and memory")
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target("cpu")
                .condition(AlertCondition.GREATER_THAN)
                .threshold(90.0)
                .conditions(List.of(
                        AlertRuleCondition.builder()
                                .target("cpu").operator(AlertCondition.GREATER_THAN).threshold(90.0).build(),
                        AlertRuleCondition.builder()
                                .target("memory").operator(AlertCondition.GREATER_THAN).threshold(85.0).build()))
                .logicOperator(operator)
                .durationMinutes(1)
                .active(true)
                .build();
    }

    private void metricValue(String metric, double value) {
        when(metricQueryService.latestValue(eq(OWNER_ID), eq(APP_ID), eq(metric), any(Instant.class)))
                .thenReturn(OptionalDouble.of(value));
    }

    @Test
    void compoundAndFiresWhenAllConditionsBreach() {
        AlertRule rule = compoundRule(LogicOperator.AND);
        rule.setBreachStartedAt(NOW.minusSeconds(61));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        metricValue("cpu", 95.0);
        metricValue("memory", 90.0);
        noOpenIncident(1L);

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());
        assertEquals(2, event.get().conditions().size());
        assertTrue(event.get().conditions().stream().allMatch(AlertEvent.ConditionObservation::breaching));
    }

    @Test
    void compoundAndDoesNotFireWhenOnlyOneConditionBreaches() {
        AlertRule rule = compoundRule(LogicOperator.AND);
        rule.setBreachStartedAt(NOW.minusSeconds(120));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        metricValue("cpu", 95.0);
        metricValue("memory", 10.0);

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isEmpty());
        verify(incidentRepository, never()).save(any());
        assertNull(rule.getBreachStartedAt());
    }

    @Test
    void compoundOrFiresWhenAnyConditionBreaches() {
        AlertRule rule = compoundRule(LogicOperator.OR);
        rule.setBreachStartedAt(NOW.minusSeconds(61));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        metricValue("cpu", 95.0);
        metricValue("memory", 10.0);
        noOpenIncident(1L);

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());
    }

    @Test
    void compoundAndWithMissingConditionLeavesStateUntouched() {
        AlertRule rule = compoundRule(LogicOperator.AND);
        Instant breachStartedAt = NOW.minusSeconds(120);
        rule.setBreachStartedAt(breachStartedAt);
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        metricValue("cpu", 95.0);
        when(metricQueryService.latestValue(eq(OWNER_ID), eq(APP_ID), eq("memory"), any(Instant.class)))
                .thenReturn(OptionalDouble.empty());

        Optional<AlertEvent> event = service.evaluate(1L, NOW);

        assertTrue(event.isEmpty());
        assertEquals(breachStartedAt, rule.getBreachStartedAt());
        verify(ruleRepository, never()).save(any());
        verify(incidentRepository, never()).save(any());
    }
}
