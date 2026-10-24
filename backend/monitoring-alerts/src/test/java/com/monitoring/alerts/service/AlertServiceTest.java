package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.response.AlertIncidentResponse;
import com.monitoring.alerts.dto.response.AlertRuleResponse;
import com.monitoring.alerts.dto.request.AlertConditionRequest;
import com.monitoring.alerts.dto.request.CreateAlertRuleRequest;
import com.monitoring.alerts.dto.request.CreateNotificationChannelRequest;
import com.monitoring.alerts.dto.request.PreviewLogCountRequest;
import com.monitoring.alerts.dto.response.LogCountPreviewResponse;
import com.monitoring.alerts.dto.response.NotificationChannelResponse;
import com.monitoring.alerts.dto.request.UpdateAlertRuleRequest;
import com.monitoring.alerts.dto.request.UpdateIncidentNotesRequest;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.exception.AlertIncidentAccessDeniedException;
import com.monitoring.alerts.exception.AlertIncidentNotFoundException;
import com.monitoring.alerts.exception.AlertRuleAccessDeniedException;
import com.monitoring.alerts.exception.AlertPreviewRateLimitedException;
import com.monitoring.alerts.exception.AlertRuleNotFoundException;
import com.monitoring.alerts.exception.InvalidAlertRuleException;
import com.monitoring.alerts.exception.LogAlertRuleLimitExceededException;
import com.monitoring.alerts.exception.InvalidIncidentNoteException;
import com.monitoring.alerts.exception.InvalidIncidentStateException;
import com.monitoring.alerts.exception.InvalidWebhookUrlException;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.healthcheck.dto.response.HealthCheckResultResponse;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.service.HealthCheckService;
import com.monitoring.healthcheck.service.SsrfGuard;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.service.LogQueryService;
import com.monitoring.revisions.entity.ConfigChangeType;
import com.monitoring.revisions.entity.ConfigResourceType;
import com.monitoring.revisions.entity.ConfigRevision;
import com.monitoring.revisions.exception.ConfigRevisionRestoreConflictException;
import com.monitoring.revisions.exception.InvalidConfigSnapshotException;
import com.monitoring.revisions.service.ConfigRevisionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long APP_ID = 7L;

    private AlertRuleRepository ruleRepository;
    private NotificationChannelRepository channelRepository;
    private AlertIncidentRepository incidentRepository;
    private UserRepository userRepository;
    private ApplicationService applicationService;
    private HealthCheckService healthCheckService;
    private AlertNotificationDispatcher notificationDispatcher;
    private LogQueryService logQueryService;
    private AlertPreviewRateLimiter previewRateLimiter;
    private ConfigRevisionService revisionService;
    private AlertService service;

    @BeforeEach
    void setUp() {
        ruleRepository = Mockito.mock(AlertRuleRepository.class);
        channelRepository = Mockito.mock(NotificationChannelRepository.class);
        incidentRepository = Mockito.mock(AlertIncidentRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        applicationService = Mockito.mock(ApplicationService.class);
        healthCheckService = Mockito.mock(HealthCheckService.class);
        notificationDispatcher = Mockito.mock(AlertNotificationDispatcher.class);
        logQueryService = Mockito.mock(LogQueryService.class);
        previewRateLimiter = Mockito.mock(AlertPreviewRateLimiter.class);
        revisionService = Mockito.mock(ConfigRevisionService.class);
        SsrfGuard ssrfGuard = new SsrfGuard();
        service = new AlertService(ruleRepository, channelRepository, incidentRepository, userRepository,
                applicationService, healthCheckService, ssrfGuard,
                new NotificationUrlPolicy(ssrfGuard, false), notificationDispatcher,
                logQueryService, previewRateLimiter, revisionService,
                new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());
        when(incidentRepository.save(any(AlertIncident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
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

    private CreateAlertRuleRequest logRule() {
        return CreateAlertRuleRequest.builder()
                .name("Checkout timeouts")
                .applicationId(APP_ID)
                .type(AlertRuleType.LOG)
                .logLevel(LogLevel.ERROR)
                .logQuery("  timeout  ")
                .windowMinutes(5)
                .comparison(AlertCondition.GREATER_OR_EQUAL)
                .countThreshold(10L)
                .durationMinutes(1)
                .active(true)
                .build();
    }

    @Test
    void createLogRulePersistsFiltersAndLegacyColumns() {
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID)).thenReturn(new Application());

        AlertRuleResponse response = service.createRule(OWNER_ID, logRule());

        assertEquals(AlertRuleType.LOG, response.getType());
        assertEquals(APP_ID, response.getApplicationId());
        assertEquals("logs", response.getTarget());
        assertEquals(LogLevel.ERROR, response.getLogLevel());
        assertEquals("timeout", response.getLogQuery());
        assertEquals(5, response.getWindowMinutes());
        assertEquals(AlertCondition.GREATER_OR_EQUAL, response.getComparison());
        assertEquals(10L, response.getCountThreshold());
        assertEquals(10.0, response.getThreshold());
        assertTrue(response.getConditions().isEmpty());
    }

    @Test
    void createLogRuleWithoutLevelOrQueryIsRejected() {
        CreateAlertRuleRequest request = logRule();
        request.setLogLevel(null);
        request.setLogQuery("  ");

        assertThrows(InvalidAlertRuleException.class, () -> service.createRule(OWNER_ID, request));
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void activeLogRuleLimitIsEnforced() {
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID)).thenReturn(new Application());
        when(ruleRepository.countByOrganizationIdAndTypeAndActiveTrue(OWNER_ID, AlertRuleType.LOG))
                .thenReturn((long) AlertService.MAX_ACTIVE_LOG_RULES);

        assertThrows(LogAlertRuleLimitExceededException.class,
                () -> service.createRule(OWNER_ID, logRule()));
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void metricRuleRejectsLogFields() {
        CreateAlertRuleRequest request = metricRule();
        request.setWindowMinutes(5);

        assertThrows(InvalidAlertRuleException.class, () -> service.createRule(OWNER_ID, request));
    }

    @Test
    void previewReturnsCountAndEnforcesRateLimit() {
        when(previewRateLimiter.tryAcquire(any(), any())).thenReturn(0L);
        when(logQueryService.countLogs(any(), any(), any(), any(), any(), any())).thenReturn(4L);

        LogCountPreviewResponse response = service.previewLogCount(OWNER_ID,
                PreviewLogCountRequest.builder()
                        .applicationId(APP_ID)
                        .logLevel(LogLevel.ERROR)
                        .logQuery("timeout")
                        .windowMinutes(5)
                        .build());

        assertEquals(4L, response.count());
        assertEquals(5, response.windowMinutes());
        assertTrue(response.from().isBefore(response.to()));

        when(previewRateLimiter.tryAcquire(any(), any())).thenReturn(12L);
        assertThrows(AlertPreviewRateLimitedException.class,
                () -> service.previewLogCount(OWNER_ID, PreviewLogCountRequest.builder()
                        .applicationId(APP_ID)
                        .logLevel(LogLevel.ERROR)
                        .windowMinutes(5)
                        .build()));
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
        assertEquals("https://8.8.8.8/****hook", response.getUrl());
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
        when(incidentRepository.findByRule_IdAndStatusIn(
                9L, List.of(IncidentStatus.FIRING, IncidentStatus.ACKNOWLEDGED)))
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

    private AlertIncident incident(IncidentStatus status, Long organizationId) {
        AlertRule rule = AlertRule.builder()
                .id(9L)
                .organizationId(organizationId)
                .name("High CPU")
                .build();
        return AlertIncident.builder()
                .id(3L)
                .rule(rule)
                .organizationId(organizationId)
                .status(status)
                .triggeredAt(java.time.Instant.parse("2026-10-17T10:00:00Z"))
                .build();
    }

    @Test
    void acknowledgeIncidentRecordsUserAndTimestamp() {
        AlertIncident firing = incident(IncidentStatus.FIRING, OWNER_ID);
        when(incidentRepository.findById(3L)).thenReturn(Optional.of(firing));

        AlertIncidentResponse response = service.acknowledgeIncident(OWNER_ID, 42L, 3L);

        assertEquals(IncidentStatus.ACKNOWLEDGED, response.getStatus());
        assertEquals(42L, response.getAcknowledgedBy());
        assertTrue(response.getAcknowledgedAt() != null);
        assertEquals(IncidentStatus.ACKNOWLEDGED, firing.getStatus());
    }

    @Test
    void acknowledgeResolvedIncidentIsRejected() {
        AlertIncident resolved = incident(IncidentStatus.RESOLVED, OWNER_ID);
        when(incidentRepository.findById(3L)).thenReturn(Optional.of(resolved));

        assertThrows(InvalidIncidentStateException.class,
                () -> service.acknowledgeIncident(OWNER_ID, 42L, 3L));
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void acknowledgeIncidentFromAnotherOrganizationIsDenied() {
        AlertIncident foreign = incident(IncidentStatus.FIRING, 999L);
        when(incidentRepository.findById(3L)).thenReturn(Optional.of(foreign));

        assertThrows(AlertIncidentAccessDeniedException.class,
                () -> service.acknowledgeIncident(OWNER_ID, 42L, 3L));
    }

    @Test
    void resolveIncidentRecordsUserAndTimestamp() {
        AlertIncident acknowledged = incident(IncidentStatus.ACKNOWLEDGED, OWNER_ID);
        when(incidentRepository.findById(3L)).thenReturn(Optional.of(acknowledged));

        AlertIncidentResponse response = service.resolveIncident(OWNER_ID, 42L, 3L);

        assertEquals(IncidentStatus.RESOLVED, response.getStatus());
        assertEquals(42L, response.getResolvedBy());
        assertTrue(response.getResolvedAt() != null);
    }

    @Test
    void resolvingAlreadyResolvedIncidentIsRejected() {
        AlertIncident resolved = incident(IncidentStatus.RESOLVED, OWNER_ID);
        when(incidentRepository.findById(3L)).thenReturn(Optional.of(resolved));

        assertThrows(InvalidIncidentStateException.class,
                () -> service.resolveIncident(OWNER_ID, 42L, 3L));
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void missingIncidentRaisesNotFound() {
        when(incidentRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(AlertIncidentNotFoundException.class,
                () -> service.resolveIncident(OWNER_ID, 42L, 3L));
    }

    @Test
    void notesRejectMarkupAndKeepPlainText() {
        AlertIncident firing = incident(IncidentStatus.FIRING, OWNER_ID);
        when(incidentRepository.findById(3L)).thenReturn(Optional.of(firing));

        assertThrows(InvalidIncidentNoteException.class,
                () -> service.updateIncidentNotes(OWNER_ID, 3L,
                        UpdateIncidentNotesRequest.builder().notes("<script>alert(1)</script>").build()));

        AlertIncidentResponse response = service.updateIncidentNotes(OWNER_ID, 3L,
                UpdateIncidentNotesRequest.builder().notes("  Cache stampede.  ").build());

        assertEquals("Cache stampede.", response.getNotes());
        assertEquals("Cache stampede.", firing.getNotes());
    }

    @Test
    void emptyNotesClearTheValue() {
        AlertIncident resolved = incident(IncidentStatus.RESOLVED, OWNER_ID);
        resolved.setNotes("old");
        when(incidentRepository.findById(3L)).thenReturn(Optional.of(resolved));

        AlertIncidentResponse response = service.updateIncidentNotes(OWNER_ID, 3L,
                UpdateIncidentNotesRequest.builder().notes("   ").build());

        assertEquals(null, response.getNotes());
    }

    @Test
    void listIncidentsResolvesActorNames() {
        AlertIncident acknowledged = incident(IncidentStatus.ACKNOWLEDGED, OWNER_ID);
        acknowledged.setAcknowledgedBy(42L);
        when(incidentRepository.findByOrganizationIdOrderByTriggeredAtDesc(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(acknowledged)));
        com.monitoring.auth.entity.User user = com.monitoring.auth.entity.User.builder()
                .id(42L).username("oncall").build();
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        List<AlertIncidentResponse> incidents = service.listIncidents(OWNER_ID, 10);

        assertEquals("oncall", incidents.get(0).getAcknowledgedByName());
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

    private AlertRule metricRule(String name, String target, double threshold, boolean active) {
        return AlertRule.builder()
                .id((long) name.hashCode())
                .organizationId(OWNER_ID)
                .applicationId(APP_ID)
                .name(name)
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target(target)
                .condition(AlertCondition.GREATER_THAN)
                .threshold(threshold)
                .active(active)
                .build();
    }

    @Test
    void listRulesForMetricReturnsOnlyActiveRulesReferencingIt() {
        AlertRule cpu = metricRule("High CPU", "system.cpu.usage", 90.0, true);
        AlertRule memory = metricRule("High memory", "system.memory.usage", 85.0, true);
        when(ruleRepository.findByOrganizationIdAndApplicationIdAndActiveTrueOrderByCreatedAtDesc(OWNER_ID, APP_ID))
                .thenReturn(List.of(cpu, memory));

        List<AlertRuleResponse> result = service.listRules(OWNER_ID, APP_ID, "system.cpu.usage");

        assertEquals(1, result.size());
        assertEquals("High CPU", result.get(0).getName());
    }

    @Test
    void listRulesMatchesCompoundConditionTargets() {
        AlertRule compound = AlertRule.builder()
                .id(11L)
                .organizationId(OWNER_ID)
                .applicationId(APP_ID)
                .name("CPU or memory")
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target("system.cpu.usage")
                .condition(AlertCondition.GREATER_THAN)
                .threshold(90.0)
                .conditions(List.of(
                        AlertRuleCondition.builder()
                                .target("system.cpu.usage").operator(AlertCondition.GREATER_THAN).threshold(90.0).build(),
                        AlertRuleCondition.builder()
                                .target("system.memory.usage").operator(AlertCondition.GREATER_THAN).threshold(85.0).build()))
                .logicOperator(LogicOperator.OR)
                .active(true)
                .build();
        when(ruleRepository.findByOrganizationIdAndApplicationIdAndActiveTrueOrderByCreatedAtDesc(OWNER_ID, APP_ID))
                .thenReturn(List.of(compound));

        List<AlertRuleResponse> result = service.listRules(OWNER_ID, APP_ID, "system.memory.usage");

        assertEquals(1, result.size());
        assertEquals("CPU or memory", result.get(0).getName());
    }

    @Test
    void listRulesWithoutFiltersUsesTenantScopedQuery() {
        when(ruleRepository.findByOrganizationIdOrderByCreatedAtDesc(OWNER_ID))
                .thenReturn(List.of(metricRule("High CPU", "system.cpu.usage", 90.0, true)));

        List<AlertRuleResponse> result = service.listRules(OWNER_ID);

        assertEquals(1, result.size());
        verify(ruleRepository).findByOrganizationIdOrderByCreatedAtDesc(OWNER_ID);
    }

    @Test
    void listRulesFilterNeverMatchesForeignApplication() {
        when(ruleRepository.findByOrganizationIdAndApplicationIdAndActiveTrueOrderByCreatedAtDesc(OWNER_ID, APP_ID))
                .thenReturn(List.of(metricRule("High CPU", "system.cpu.usage", 90.0, true)));

        List<AlertRuleResponse> result = service.listRules(9_999L, APP_ID, "system.cpu.usage");

        assertTrue(result.isEmpty());
        verify(ruleRepository).findByOrganizationIdAndApplicationIdAndActiveTrueOrderByCreatedAtDesc(9_999L, APP_ID);
    }

    @Test
    void createRuleRecordsASnapshotWithoutChannelSecrets() {
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID)).thenReturn(new Application());
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> {
            AlertRule saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        service.createRule(OWNER_ID, metricRule());

        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(revisionService).recordCreate(eq(OWNER_ID), eq(ConfigResourceType.ALERT_RULE),
                any(Long.class), eq(AlertService.SNAPSHOT_SCHEMA_VERSION), snapshot.capture());
        assertTrue(snapshot.getValue().contains("\"name\":\"High CPU\""));
        assertFalse(snapshot.getValue().contains("slackWebhookUrl"));
        assertFalse(snapshot.getValue().contains("emailAddress"));
        assertFalse(snapshot.getValue().contains("\"url\""));
    }

    @Test
    void updateRuleRecordsBeforeAndAfterSnapshots() {
        AlertRule rule = metricRule("Before", "system.cpu.usage", 80.0, true);
        rule.setId(11L);
        rule.setConditions(new ArrayList<>());
        when(ruleRepository.findById(11L)).thenReturn(Optional.of(rule));
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID)).thenReturn(new Application());

        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setName("After");

        service.updateRule(OWNER_ID, 11L, request);

        verify(revisionService).recordUpdate(
                eq(OWNER_ID), eq(ConfigResourceType.ALERT_RULE), eq(11L),
                eq(AlertService.SNAPSHOT_SCHEMA_VERSION),
                argThat(value -> value.contains("\"name\":\"Before\"")),
                argThat(value -> value.contains("\"name\":\"After\"")));
    }

    @Test
    void restoreRevisionAppliesTheSnapshotThroughTheUpdatePath() {
        AlertRule rule = metricRule("Current", "system.cpu.usage", 50.0, true);
        rule.setId(11L);
        rule.setConditions(new ArrayList<>());
        when(ruleRepository.findById(11L)).thenReturn(Optional.of(rule));
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID)).thenReturn(new Application());
        when(revisionService.requireRevision(OWNER_ID, ConfigResourceType.ALERT_RULE, 11L, 2))
                .thenReturn(revision(2, """
                        {"name":"Restored","type":"METRIC_THRESHOLD","applicationId":7,
                         "durationMinutes":5,"logicOperator":"AND","active":true,
                         "conditions":[{"target":"system.cpu.usage","operator":"GREATER_THAN","threshold":90.0}],
                         "logLevel":null,"logQuery":null,"windowMinutes":null,
                         "comparison":null,"countThreshold":null}
                        """));

        AlertRuleResponse response = service.restoreRevision(OWNER_ID, 11L, 2);

        assertEquals("Restored", response.getName());
        assertEquals(90.0, response.getThreshold());
        verify(revisionService).recordRestore(eq(OWNER_ID), eq(ConfigResourceType.ALERT_RULE),
                eq(11L), eq(2), eq(AlertService.SNAPSHOT_SCHEMA_VERSION), any());
    }

    @Test
    void restoringARuleWithADeletedApplicationReturnsAConflict() {
        AlertRule rule = metricRule("Current", "system.cpu.usage", 50.0, true);
        rule.setId(11L);
        rule.setConditions(new ArrayList<>());
        when(ruleRepository.findById(11L)).thenReturn(Optional.of(rule));
        when(applicationService.getOwnedApplication(APP_ID, OWNER_ID))
                .thenThrow(new ApplicationAccessDeniedException());
        when(revisionService.requireRevision(OWNER_ID, ConfigResourceType.ALERT_RULE, 11L, 1))
                .thenReturn(revision(1, """
                        {"name":"Restored","type":"METRIC_THRESHOLD","applicationId":7,
                         "durationMinutes":5,"logicOperator":"AND","active":true,
                         "conditions":[{"target":"system.cpu.usage","operator":"GREATER_THAN","threshold":90.0}],
                         "logLevel":null,"logQuery":null,"windowMinutes":null,
                         "comparison":null,"countThreshold":null}
                        """));

        assertThrows(ConfigRevisionRestoreConflictException.class,
                () -> service.restoreRevision(OWNER_ID, 11L, 1));
        assertEquals("Current", rule.getName());
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void restoringAnUnknownSnapshotFieldIsRejected() {
        AlertRule rule = metricRule("Current", "system.cpu.usage", 50.0, true);
        rule.setId(11L);
        when(ruleRepository.findById(11L)).thenReturn(Optional.of(rule));
        when(revisionService.requireRevision(OWNER_ID, ConfigResourceType.ALERT_RULE, 11L, 1))
                .thenReturn(revision(1, """
                        {"name":"Restored","type":"METRIC_THRESHOLD","applicationId":7,
                         "durationMinutes":5,"logicOperator":"AND","active":true,
                         "conditions":[],"logLevel":null,"logQuery":null,"windowMinutes":null,
                         "comparison":null,"countThreshold":null,"organizationId":999}
                        """));

        assertThrows(InvalidConfigSnapshotException.class,
                () -> service.restoreRevision(OWNER_ID, 11L, 1));
        assertEquals("Current", rule.getName());
    }

    @Test
    void deleteRuleRemovesItsHistoryInTheSameOperation() {
        AlertRule rule = metricRule("Old", "system.cpu.usage", 50.0, true);
        rule.setId(11L);
        when(ruleRepository.findById(11L)).thenReturn(Optional.of(rule));

        service.deleteRule(OWNER_ID, 11L);

        verify(revisionService).deleteAll(OWNER_ID, ConfigResourceType.ALERT_RULE, 11L);
        verify(ruleRepository).delete(rule);
    }

    private ConfigRevision revision(int number, String snapshot) {
        return ConfigRevision.builder()
                .organizationId(OWNER_ID)
                .resourceType(ConfigResourceType.ALERT_RULE)
                .resourceId(11L)
                .revisionNumber(number)
                .changeType(ConfigChangeType.UPDATE)
                .schemaVersion(AlertService.SNAPSHOT_SCHEMA_VERSION)
                .snapshot(snapshot)
                .build();
    }
}
