package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.response.AlertIncidentResponse;
import com.monitoring.alerts.dto.response.AlertRuleResponse;
import com.monitoring.alerts.dto.request.AlertConditionRequest;
import com.monitoring.alerts.dto.request.CreateAlertRuleRequest;
import com.monitoring.alerts.dto.request.CreateNotificationChannelRequest;
import com.monitoring.alerts.dto.response.NotificationChannelResponse;
import com.monitoring.alerts.dto.request.UpdateAlertRuleRequest;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.exception.AlertRuleAccessDeniedException;
import com.monitoring.alerts.exception.AlertRuleNotFoundException;
import com.monitoring.alerts.exception.InvalidAlertRuleException;
import com.monitoring.alerts.exception.InvalidWebhookUrlException;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.healthcheck.dto.response.HealthCheckResultResponse;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.service.HealthCheckService;
import com.monitoring.healthcheck.service.SsrfGuard;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.service.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long APP_ID = 7L;

    private AlertRuleRepository ruleRepository;
    private NotificationChannelRepository channelRepository;
    private AlertIncidentRepository incidentRepository;
    private ApplicationService applicationService;
    private HealthCheckService healthCheckService;
    private AlertNotificationDispatcher notificationDispatcher;
    private AlertService service;

    @BeforeEach
    void setUp() {
        ruleRepository = Mockito.mock(AlertRuleRepository.class);
        channelRepository = Mockito.mock(NotificationChannelRepository.class);
        incidentRepository = Mockito.mock(AlertIncidentRepository.class);
        applicationService = Mockito.mock(ApplicationService.class);
        healthCheckService = Mockito.mock(HealthCheckService.class);
        notificationDispatcher = Mockito.mock(AlertNotificationDispatcher.class);
        service = new AlertService(ruleRepository, channelRepository, incidentRepository,
                applicationService, healthCheckService, new SsrfGuard(), notificationDispatcher);
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelRepository.save(any(NotificationChannel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateAlertRuleRequest metricRule() {
        return CreateAlertRuleRequest.builder()
                .name("High CPU")
                .applicationId(APP_ID)
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target("  system.cpu.usage  ")
                .condition(AlertCondition.GREATER_THAN)
                .threshold(90.0)
                .durationMinutes(5)
                .active(true)
                .build();
    }

    @Test
    void createMetricRuleVerifiesOwnershipAndTrimsTarget() {
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID)).thenReturn(new Application());

        AlertRuleResponse response = service.createRule(OWNER_ID, metricRule());

        assertEquals(APP_ID, response.getApplicationId());
        assertEquals("system.cpu.usage", response.getTarget());
        assertEquals(OWNER_ID, response.getOrganizationId());
        assertTrue(response.isActive());
    }

    @Test
    void createMetricRuleWithoutApplicationIsRejected() {
        CreateAlertRuleRequest request = metricRule();
        request.setApplicationId(null);

        assertThrows(InvalidAlertRuleException.class, () -> service.createRule(OWNER_ID, request));
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void createMetricRuleForForeignApplicationIsDenied() {
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID))
                .thenThrow(new ApplicationAccessDeniedException());

        assertThrows(ApplicationAccessDeniedException.class,
                () -> service.createRule(OWNER_ID, metricRule()));
    }

    @Test
    void createHealthCheckRuleRequiresOwnedCheck() {
        when(healthCheckService.listResults(OWNER_ID, 5L, 1))
                .thenThrow(new com.monitoring.healthcheck.exception.HealthCheckNotFoundException(5L));

        CreateAlertRuleRequest request = CreateAlertRuleRequest.builder()
                .name("API down")
                .type(AlertRuleType.HEALTH_CHECK)
                .target("5")
                .condition(AlertCondition.EQUAL)
                .threshold(0.0)
                .durationMinutes(1)
                .build();

        assertThrows(InvalidAlertRuleException.class, () -> service.createRule(OWNER_ID, request));
    }

    @Test
    void createHealthCheckRuleRejectsNonNumericTarget() {
        CreateAlertRuleRequest request = CreateAlertRuleRequest.builder()
                .name("API down")
                .type(AlertRuleType.HEALTH_CHECK)
                .target("not-a-number")
                .condition(AlertCondition.EQUAL)
                .threshold(0.0)
                .durationMinutes(1)
                .build();

        assertThrows(InvalidAlertRuleException.class, () -> service.createRule(OWNER_ID, request));
    }

    @Test
    void createHealthCheckRulePersistsWithNullApplication() {
        when(healthCheckService.listResults(OWNER_ID, 5L, 1))
                .thenReturn(List.of(HealthCheckResultResponse.builder().status(HealthStatus.UP).build()));

        CreateAlertRuleRequest request = CreateAlertRuleRequest.builder()
                .name("API down")
                .type(AlertRuleType.HEALTH_CHECK)
                .target("5")
                .condition(AlertCondition.EQUAL)
                .threshold(0.0)
                .durationMinutes(1)
                .build();

        AlertRuleResponse response = service.createRule(OWNER_ID, request);
        assertEquals(null, response.getApplicationId());
        assertEquals(AlertRuleType.HEALTH_CHECK, response.getType());
    }

    @Test
    void createChannelRequiresHttps() {
        CreateNotificationChannelRequest request = CreateNotificationChannelRequest.builder()
                .type(NotificationChannelType.WEBHOOK)
                .url("http://8.8.8.8/hook")
                .build();

        assertThrows(InvalidWebhookUrlException.class, () -> service.createChannel(OWNER_ID, request));
        verify(channelRepository, never()).save(any());
    }

    @Test
    void createChannelAcceptsPublicHttpsUrl() {
        CreateNotificationChannelRequest request = CreateNotificationChannelRequest.builder()
                .type(NotificationChannelType.WEBHOOK)
                .url("https://8.8.8.8/hook")
                .build();

        NotificationChannelResponse response = service.createChannel(OWNER_ID, request);
        assertEquals("https://8.8.8.8/hook", response.getUrl());
        assertTrue(response.isActive());
    }

    @Test
    void foreignRuleUpdateIsDenied() {
        AlertRule rule = AlertRule.builder().id(9L).organizationId(OWNER_ID).build();
        when(ruleRepository.findById(9L)).thenReturn(Optional.of(rule));

        assertThrows(AlertRuleAccessDeniedException.class,
                () -> service.updateRule(999L, 9L, UpdateAlertRuleRequest.builder().name("x").build()));
    }

    @Test
    void missingRuleRaisesNotFound() {
        when(ruleRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(AlertRuleNotFoundException.class,
                () -> service.updateRule(OWNER_ID, 9L, UpdateAlertRuleRequest.builder().name("x").build()));
    }

    @Test
    void deactivatingRuleResolvesOpenIncidents() {
        AlertRule rule = AlertRule.builder()
                .id(9L)
                .organizationId(OWNER_ID)
                .name("High CPU")
                .type(AlertRuleType.METRIC_THRESHOLD)
                .applicationId(APP_ID)
                .target("system.cpu.usage")
                .condition(AlertCondition.GREATER_THAN)
                .threshold(90.0)
                .durationMinutes(5)
                .active(true)
                .build();
        when(ruleRepository.findById(9L)).thenReturn(Optional.of(rule));
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID)).thenReturn(new Application());

        AlertIncident firing = AlertIncident.builder()
                .id(3L)
                .rule(rule)
                .organizationId(OWNER_ID)
                .status(IncidentStatus.FIRING)
                .build();
        when(incidentRepository.findByRule_IdAndStatus(9L, IncidentStatus.FIRING))
                .thenReturn(List.of(firing));

        AlertRuleResponse response = service.updateRule(OWNER_ID, 9L,
                UpdateAlertRuleRequest.builder().active(false).build());

        assertFalse(response.isActive());
        assertEquals(IncidentStatus.RESOLVED, firing.getStatus());
        assertTrue(firing.getResolvedAt() != null);
        verify(incidentRepository).save(firing);
    }

    @Test
    void listIncidentsClampsLimit() {
        when(incidentRepository.findByOrganizationIdOrderByTriggeredAtDesc(any(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        List<AlertIncidentResponse> result = service.listIncidents(OWNER_ID, 100_000);

        assertTrue(result.isEmpty());
        verify(incidentRepository).findByOrganizationIdOrderByTriggeredAtDesc(
                org.mockito.ArgumentMatchers.eq(OWNER_ID),
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageSize() == AlertService.MAX_INCIDENTS_LIMIT));
    }

    @Test
    void createCompoundMetricRulePersistsConditions() {
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID)).thenReturn(new Application());

        CreateAlertRuleRequest request = CreateAlertRuleRequest.builder()
                .name("CPU and memory")
                .applicationId(APP_ID)
                .type(AlertRuleType.METRIC_THRESHOLD)
                .conditions(List.of(
                        new AlertConditionRequest("system.cpu.usage", AlertCondition.GREATER_THAN, 90.0),
                        new AlertConditionRequest("system.memory.usage", AlertCondition.GREATER_THAN, 85.0)))
                .logicOperator(LogicOperator.OR)
                .durationMinutes(5)
                .active(true)
                .build();

        AlertRuleResponse response = service.createRule(OWNER_ID, request);

        assertEquals(2, response.getConditions().size());
        assertEquals(LogicOperator.OR, response.getLogicOperator());
        // The legacy columns mirror the first condition for backward compatibility.
        assertEquals("system.cpu.usage", response.getTarget());
        assertEquals(90.0, response.getThreshold());
    }

    @Test
    void createRuleWithTooManyConditionsIsRejected() {
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID)).thenReturn(new Application());

        List<AlertConditionRequest> conditions = new java.util.ArrayList<>();
        for (int i = 0; i < AlertRule.MAX_CONDITIONS + 1; i++) {
            conditions.add(new AlertConditionRequest("metric." + i, AlertCondition.GREATER_THAN, 1.0));
        }
        CreateAlertRuleRequest request = CreateAlertRuleRequest.builder()
                .name("Too many")
                .applicationId(APP_ID)
                .type(AlertRuleType.METRIC_THRESHOLD)
                .conditions(conditions)
                .durationMinutes(5)
                .build();

        assertThrows(InvalidAlertRuleException.class, () -> service.createRule(OWNER_ID, request));
        verify(ruleRepository, never()).save(any());
    }
}
