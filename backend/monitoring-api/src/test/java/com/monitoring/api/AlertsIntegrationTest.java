package com.monitoring.api;

import com.monitoring.alerts.dto.response.AlertIncidentResponse;
import com.monitoring.alerts.dto.response.AlertRuleResponse;
import com.monitoring.alerts.dto.response.LogCountPreviewResponse;
import com.monitoring.alerts.dto.request.AlertConditionRequest;
import com.monitoring.alerts.dto.request.CreateAlertRuleRequest;
import com.monitoring.alerts.dto.request.CreateMaintenanceWindowRequest;
import com.monitoring.alerts.dto.request.CreateNotificationChannelRequest;
import com.monitoring.alerts.dto.request.PreviewLogCountRequest;
import com.monitoring.alerts.dto.request.UpdateMaintenanceWindowRequest;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.alerts.entity.MaintenanceScopeType;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.exception.AlertRuleAccessDeniedException;
import com.monitoring.alerts.exception.InvalidWebhookUrlException;
import com.monitoring.alerts.exception.MaintenanceWindowAccessDeniedException;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.alerts.repository.MaintenanceWindowRepository;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.alerts.service.AlertEvaluationService;
import com.monitoring.alerts.service.AlertService;
import com.monitoring.alerts.service.MaintenanceWindowService;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.collector.service.LogQueryService;
import com.monitoring.healthcheck.dto.request.CreateHealthCheckRequest;
import com.monitoring.healthcheck.dto.response.HealthCheckResponse;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import com.monitoring.healthcheck.repository.HealthCheckResultRepository;
import com.monitoring.healthcheck.service.HealthCheckOutcome;
import com.monitoring.healthcheck.service.HealthCheckService;
import com.monitoring.apps.dto.request.ApplicationCreateRequest;
import com.monitoring.apps.dto.response.ApplicationWithKeyResponse;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.apps.service.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies alert evaluation, tenant isolation and webhook URL validation against a real
 * persistence context. Both schedulers are disabled so no background evaluation or outbound
 * requests happen; evaluation is driven explicitly through {@link AlertEvaluationService}.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:alerts-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.healthcheck.scheduler.enabled=false",
        "monitoring.alerts.scheduler.enabled=false"
})
class AlertsIntegrationTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final String METRIC = "system.cpu.usage";
    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertEvaluationService evaluationService;

    @Autowired
    private MaintenanceWindowService maintenanceWindowService;

    @Autowired
    private MaintenanceWindowRepository maintenanceWindowRepository;

    @Autowired
    private AlertRuleRepository ruleRepository;

    @Autowired
    private AlertIncidentRepository incidentRepository;

    @Autowired
    private NotificationChannelRepository channelRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MetricSampleRepository metricSampleRepository;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private LogQueryService logQueryService;

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private HealthCheckConfigRepository healthCheckConfigRepository;

    @Autowired
    private HealthCheckResultRepository healthCheckResultRepository;

    @BeforeEach
    void setUp() {
        maintenanceWindowRepository.deleteAll();
        incidentRepository.deleteAll();
        ruleRepository.deleteAll();
        channelRepository.deleteAll();
        logEventRepository.deleteAll();
        metricSampleRepository.deleteAll();
        healthCheckResultRepository.deleteAll();
        healthCheckConfigRepository.deleteAll();
        applicationRepository.deleteAll();
    }

    private Application createApplication(long organizationId, String name) {
        ApplicationCreateRequest request = new ApplicationCreateRequest();
        request.setName(name);
        ApplicationWithKeyResponse created = applicationService.createApplication(request, organizationId);
        return applicationRepository.findById(created.id()).orElseThrow();
    }

    private AlertRuleResponse createMetricRule(long organizationId, long applicationId) {
        return alertService.createRule(organizationId, CreateAlertRuleRequest.builder()
                .name("High CPU")
                .applicationId(applicationId)
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target(METRIC)
                .condition(AlertCondition.GREATER_THAN)
                .threshold(90.0)
                .durationMinutes(1)
                .active(true)
                .build());
    }

    private AlertRuleResponse createLogRule(long organizationId, long applicationId) {
        return alertService.createRule(organizationId, CreateAlertRuleRequest.builder()
                .name("Checkout timeouts")
                .applicationId(applicationId)
                .type(AlertRuleType.LOG)
                .logLevel(LogLevel.ERROR)
                .logQuery("timeout")
                .windowMinutes(5)
                .comparison(AlertCondition.GREATER_OR_EQUAL)
                .countThreshold(2L)
                .durationMinutes(1)
                .active(true)
                .build());
    }

    private void saveLog(Application application, LogLevel level, String message, Instant timestamp) {
        logEventRepository.save(LogEvent.builder()
                .application(application)
                .level(level)
                .message(message)
                .timestamp(timestamp)
                .receivedAt(timestamp)
                .build());
    }

    @Test
    void logRuleCountsOnlyMatchingLogsInWindowAndFires() {
        Application application = createApplication(OWNER_ID, "checkout");
        Application foreign = createApplication(OTHER_ID, "foreign");
        saveLog(application, LogLevel.ERROR, "database timeout", NOW.minusSeconds(60));
        saveLog(application, LogLevel.ERROR, "timeout reading cache", NOW.minusSeconds(30));
        saveLog(application, LogLevel.INFO, "timeout is expected here", NOW.minusSeconds(20));
        saveLog(application, LogLevel.ERROR, "unrelated failure", NOW.minusSeconds(10));
        saveLog(application, LogLevel.ERROR, "timeout outside window", NOW.minusSeconds(400));
        saveLog(foreign, LogLevel.ERROR, "timeout from another tenant", NOW.minusSeconds(20));

        AlertRuleResponse rule = createLogRule(OWNER_ID, application.getId());

        assertTrue(evaluationService.evaluate(rule.getId(), NOW).isEmpty());
        var event = evaluationService.evaluate(rule.getId(), NOW.plusSeconds(61));

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());
        assertEquals(2.0, event.get().observedValue());
        assertEquals(2L, event.get().logDetails().observedCount());
        assertEquals(LogLevel.ERROR, event.get().logDetails().level());
        assertEquals("timeout", event.get().logDetails().query());
        assertEquals(5, event.get().logDetails().windowMinutes());
        assertEquals("checkout", event.get().logDetails().applicationName());

        AlertIncidentResponse incident = alertService.listIncidents(OWNER_ID, null).get(0);
        assertEquals(AlertRuleType.LOG, incident.getRuleType());
        assertEquals(application.getId(), incident.getApplicationId());
        assertEquals(5, incident.getWindowMinutes());
    }

    @Test
    void logRuleCannotUseAnotherTenantsApplication() {
        Application foreign = createApplication(OTHER_ID, "foreign");

        assertThrows(com.monitoring.apps.exception.ApplicationAccessDeniedException.class,
                () -> createLogRule(OWNER_ID, foreign.getId()));
    }

    @Test
    void logRuleResolvesWhenCountDropsBelowThreshold() {
        Application application = createApplication(OWNER_ID, "checkout");
        saveLog(application, LogLevel.ERROR, "database timeout", NOW.minusSeconds(30));
        saveLog(application, LogLevel.ERROR, "timeout reading cache", NOW.minusSeconds(20));
        AlertRuleResponse rule = createLogRule(OWNER_ID, application.getId());

        assertTrue(evaluationService.evaluate(rule.getId(), NOW).isEmpty());
        assertEquals(IncidentStatus.FIRING,
                evaluationService.evaluate(rule.getId(), NOW.plusSeconds(61)).orElseThrow().status());

        var recovery = evaluationService.evaluate(rule.getId(), NOW.plusSeconds(400));

        assertTrue(recovery.isPresent());
        assertEquals(IncidentStatus.RESOLVED, recovery.get().status());
        assertEquals(0L, recovery.get().logDetails().observedCount());
        assertEquals(IncidentStatus.RESOLVED,
                alertService.listIncidents(OWNER_ID, null).get(0).getStatus());
    }

    @Test
    void logRuleCountEscapesLikeWildcards() {
        Application application = createApplication(OWNER_ID, "checkout");
        Instant from = NOW.minusSeconds(300);
        saveLog(application, LogLevel.ERROR, "100% timeout", NOW.minusSeconds(30));
        saveLog(application, LogLevel.ERROR, "100x timeout", NOW.minusSeconds(20));

        long literal = logQueryService.countLogs(
                OWNER_ID, application.getId(), LogLevel.ERROR, "100% timeout", from, NOW);
        long patternWouldMatch = logQueryService.countLogs(
                OWNER_ID, application.getId(), LogLevel.ERROR, "100x timeout", from, NOW);

        assertEquals(1, literal);
        assertEquals(1, patternWouldMatch);
    }

    @Test
    void logPreviewCountsCurrentWindowAndIsTenantScoped() {
        Application application = createApplication(OWNER_ID, "checkout");
        Instant now = Instant.now();
        saveLog(application, LogLevel.ERROR, "timeout one", now.minusSeconds(30));
        saveLog(application, LogLevel.ERROR, "timeout two", now.minusSeconds(20));
        saveLog(application, LogLevel.INFO, "timeout three", now.minusSeconds(10));
        saveLog(application, LogLevel.ERROR, "timeout too old", now.minusSeconds(600));

        LogCountPreviewResponse preview = alertService.previewLogCount(OWNER_ID,
                PreviewLogCountRequest.builder()
                        .applicationId(application.getId())
                        .logLevel(LogLevel.ERROR)
                        .logQuery("timeout")
                        .windowMinutes(5)
                        .build());

        assertEquals(2, preview.count());
        assertEquals(5, preview.windowMinutes());

        assertThrows(com.monitoring.apps.exception.ApplicationAccessDeniedException.class,
                () -> alertService.previewLogCount(OTHER_ID, PreviewLogCountRequest.builder()
                        .applicationId(application.getId())
                        .logLevel(LogLevel.ERROR)
                        .windowMinutes(5)
                        .build()));
    }

    @Test
    void metricBreachCreatesIncidentAfterDuration() {
        Application application = createApplication(OWNER_ID, "web");
        AlertRuleResponse rule = createMetricRule(OWNER_ID, application.getId());

        metricSampleRepository.save(MetricSample.builder()
                .application(application)
                .name(METRIC)
                .value(95.0)
                .timestamp(NOW)
                .receivedAt(NOW)
                .build());

        assertTrue(evaluationService.evaluate(rule.getId(), NOW).isEmpty());

        var event = evaluationService.evaluate(rule.getId(), NOW.plusSeconds(61));

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());

        List<AlertIncidentResponse> incidents = alertService.listIncidents(OWNER_ID, null);
        assertEquals(1, incidents.size());
        assertEquals(IncidentStatus.FIRING, incidents.get(0).getStatus());
        assertEquals(rule.getId(), incidents.get(0).getRuleId());
    }

    @Test
    void incidentLifecycleAcknowledgesNotesResolvesAndFilters() {
        Application application = createApplication(OWNER_ID, "web");
        AlertRuleResponse rule = createMetricRule(OWNER_ID, application.getId());

        metricSampleRepository.save(MetricSample.builder()
                .application(application)
                .name(METRIC)
                .value(95.0)
                .timestamp(NOW)
                .receivedAt(NOW)
                .build());

        assertTrue(evaluationService.evaluate(rule.getId(), NOW).isEmpty());
        evaluationService.evaluate(rule.getId(), NOW.plusSeconds(61)).orElseThrow();

        AlertIncidentResponse firing = alertService.listIncidents(OWNER_ID, null,
                IncidentStatus.FIRING).get(0);

        AlertIncidentResponse acknowledged = alertService.acknowledgeIncident(
                OWNER_ID, OWNER_ID, firing.getId());
        assertEquals(IncidentStatus.ACKNOWLEDGED, acknowledged.getStatus());
        assertEquals(OWNER_ID, acknowledged.getAcknowledgedBy());
        assertTrue(acknowledged.getAcknowledgedAt() != null);

        AlertIncidentResponse noted = alertService.updateIncidentNotes(OWNER_ID, firing.getId(),
                com.monitoring.alerts.dto.request.UpdateIncidentNotesRequest.builder()
                        .notes("Root cause: cache stampede.")
                        .build());
        assertEquals("Root cause: cache stampede.", noted.getNotes());

        assertEquals(1, alertService.listIncidents(OWNER_ID, null,
                IncidentStatus.ACKNOWLEDGED).size());

        AlertIncidentResponse resolved = alertService.resolveIncident(
                OWNER_ID, OWNER_ID, firing.getId());
        assertEquals(IncidentStatus.RESOLVED, resolved.getStatus());
        assertEquals(OWNER_ID, resolved.getResolvedBy());
        assertTrue(resolved.getResolvedAt() != null);

        assertThrows(com.monitoring.alerts.exception.InvalidIncidentStateException.class,
                () -> alertService.resolveIncident(OWNER_ID, OWNER_ID, firing.getId()));
    }

    @Test
    void healthCheckRuleFiresWhenCheckIsDown() {
        HealthCheckResponse check = healthCheckService.create(OWNER_ID, CreateHealthCheckRequest.builder()
                .name("API")
                .targetUrl("http://8.8.8.8/health")
                .method(HealthCheckMethod.GET)
                .expectedStatusCode(200)
                .intervalSeconds(30)
                .active(true)
                .build());

        AlertRuleResponse rule = alertService.createRule(OWNER_ID, CreateAlertRuleRequest.builder()
                .name("API down")
                .type(AlertRuleType.HEALTH_CHECK)
                .target(String.valueOf(check.getId()))
                .condition(AlertCondition.EQUAL)
                .threshold(0.0)
                .durationMinutes(1)
                .build());

        healthCheckService.recordResult(check.getId(), new HealthCheckOutcome(HealthStatus.DOWN, 50, "boom"), NOW);

        assertTrue(evaluationService.evaluate(rule.getId(), NOW).isEmpty());
        var event = evaluationService.evaluate(rule.getId(), NOW.plusSeconds(61));

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());
        assertEquals(null, event.get().applicationId());
    }

    @Test
    void rulesAndChannelsAreTenantScoped() {
        Application application = createApplication(OWNER_ID, "web");
        AlertRuleResponse rule = createMetricRule(OWNER_ID, application.getId());

        assertEquals(0, alertService.listRules(OTHER_ID).size());
        assertThrows(AlertRuleAccessDeniedException.class,
                () -> alertService.updateRule(OTHER_ID, rule.getId(),
                        com.monitoring.alerts.dto.request.UpdateAlertRuleRequest.builder().name("x").build()));
        assertThrows(AlertRuleAccessDeniedException.class,
                () -> alertService.deleteRule(OTHER_ID, rule.getId()));
    }

    @Test
    void metricRuleLookupFiltersByApplicationMetricAndTenant() {
        Application application = createApplication(OWNER_ID, "web");
        Application otherApplication = createApplication(OWNER_ID, "api");
        createMetricRule(OWNER_ID, application.getId());

        // Same application, different metric: must not match.
        alertService.createRule(OWNER_ID, CreateAlertRuleRequest.builder()
                .name("High memory")
                .applicationId(application.getId())
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target("system.memory.usage")
                .condition(AlertCondition.GREATER_THAN)
                .threshold(85.0)
                .durationMinutes(1)
                .active(true)
                .build());
        // Same metric, different application: must not match.
        alertService.createRule(OWNER_ID, CreateAlertRuleRequest.builder()
                .name("CPU on api")
                .applicationId(otherApplication.getId())
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target(METRIC)
                .condition(AlertCondition.GREATER_THAN)
                .threshold(80.0)
                .durationMinutes(1)
                .active(true)
                .build());
        // Same application and metric but deactivated: must not match.
        alertService.createRule(OWNER_ID, CreateAlertRuleRequest.builder()
                .name("Muted CPU")
                .applicationId(application.getId())
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target(METRIC)
                .condition(AlertCondition.GREATER_THAN)
                .threshold(70.0)
                .durationMinutes(1)
                .active(false)
                .build());

        List<AlertRuleResponse> matches = alertService.listRules(OWNER_ID, application.getId(), METRIC);

        assertEquals(1, matches.size());
        assertEquals("High CPU", matches.get(0).getName());
        // Another tenant never sees the rule for the same application id.
        assertEquals(0, alertService.listRules(OTHER_ID, application.getId(), METRIC).size());
    }

    @Test
    void webhookChannelRequiresHttpsAndPublicHost() {
        var created = alertService.createChannel(OWNER_ID, CreateNotificationChannelRequest.builder()
                .type(NotificationChannelType.WEBHOOK)
                .url("https://8.8.8.8/hook")
                .build());
        assertEquals("https://8.8.8.8/hook", created.getUrl());
        assertEquals(1, alertService.listChannels(OWNER_ID).size());

        assertThrows(InvalidWebhookUrlException.class,
                () -> alertService.createChannel(OWNER_ID, CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.WEBHOOK)
                        .url("http://8.8.8.8/hook")
                        .build()));
        assertEquals(1, channelRepository.count());
    }

    @Test
    void compoundConditionsArePersistedAndEvaluated() {
        Application application = createApplication(OWNER_ID, "web");
        String memoryMetric = "system.memory.usage";
        AlertRuleResponse rule = alertService.createRule(OWNER_ID, CreateAlertRuleRequest.builder()
                .name("CPU or memory")
                .applicationId(application.getId())
                .type(AlertRuleType.METRIC_THRESHOLD)
                .conditions(List.of(
                        new AlertConditionRequest(METRIC, AlertCondition.GREATER_THAN, 90.0),
                        new AlertConditionRequest(memoryMetric, AlertCondition.GREATER_THAN, 85.0)))
                .logicOperator(LogicOperator.OR)
                .durationMinutes(1)
                .active(true)
                .build());

        assertEquals(2, rule.getConditions().size());
        assertEquals(LogicOperator.OR, rule.getLogicOperator());

        AlertRuleResponse reloaded = alertService.listRules(OWNER_ID).get(0);
        assertEquals(2, reloaded.getConditions().size());
        assertEquals(LogicOperator.OR, reloaded.getLogicOperator());

        metricSampleRepository.save(MetricSample.builder()
                .application(application).name(METRIC).value(95.0).timestamp(NOW).receivedAt(NOW).build());
        metricSampleRepository.save(MetricSample.builder()
                .application(application).name(memoryMetric).value(10.0).timestamp(NOW).receivedAt(NOW).build());

        assertTrue(evaluationService.evaluate(rule.getId(), NOW).isEmpty());
        var event = evaluationService.evaluate(rule.getId(), NOW.plusSeconds(61));

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());
        assertEquals(2, event.get().conditions().size());
    }

    @Test
    void emailChannelIsPersisted() {
        var created = alertService.createChannel(OWNER_ID, CreateNotificationChannelRequest.builder()
                .type(NotificationChannelType.EMAIL)
                .emailAddress("oncall@example.com")
                .build());

        assertEquals(NotificationChannelType.EMAIL, created.getType());
        assertEquals("oncall@example.com", created.getEmailAddress());
        assertEquals(1, alertService.listChannels(OWNER_ID).size());
    }

    @Test
    void maintenanceWindowSuppressesIncidentAndResumesAfterItEnds() {
        Instant base = Instant.now();
        Application application = createApplication(OWNER_ID, "web");
        AlertRuleResponse rule = createMetricRule(OWNER_ID, application.getId());

        metricSampleRepository.save(MetricSample.builder()
                .application(application)
                .name(METRIC)
                .value(95.0)
                .timestamp(base)
                .receivedAt(base)
                .build());

        maintenanceWindowService.create(OWNER_ID, CreateMaintenanceWindowRequest.builder()
                .name("Deploy")
                .scopeType(MaintenanceScopeType.APPLICATION)
                .scopeId(application.getId())
                .startTime(base.minusSeconds(60))
                .endTime(base.plusSeconds(120))
                .build());

        assertTrue(evaluationService.evaluate(rule.getId(), base).isEmpty());
        assertTrue(evaluationService.evaluate(rule.getId(), base.plusSeconds(61)).isEmpty());
        assertEquals(0, alertService.listIncidents(OWNER_ID, null).size());

        var event = evaluationService.evaluate(rule.getId(), base.plusSeconds(130));

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());
        assertEquals(1, alertService.listIncidents(OWNER_ID, null).size());
    }

    @Test
    void maintenanceWindowCanBeEndedEarlyAndIsTenantScoped() {
        Instant base = Instant.now();
        Application application = createApplication(OWNER_ID, "web");
        AlertRuleResponse rule = createMetricRule(OWNER_ID, application.getId());

        metricSampleRepository.save(MetricSample.builder()
                .application(application)
                .name(METRIC)
                .value(95.0)
                .timestamp(base)
                .receivedAt(base)
                .build());

        var window = maintenanceWindowService.create(OWNER_ID, CreateMaintenanceWindowRequest.builder()
                .name("Deploy")
                .scopeType(MaintenanceScopeType.SPECIFIC_RULE)
                .scopeId(rule.getId())
                .startTime(base.minusSeconds(60))
                .endTime(base.plusSeconds(7200))
                .build());

        assertEquals(0, maintenanceWindowService.list(OTHER_ID).size());
        assertThrows(MaintenanceWindowAccessDeniedException.class,
                () -> maintenanceWindowService.update(OTHER_ID, window.getId(),
                        UpdateMaintenanceWindowRequest.builder().active(false).build()));

        assertTrue(evaluationService.evaluate(rule.getId(), base).isEmpty());
        maintenanceWindowService.update(OWNER_ID, window.getId(),
                UpdateMaintenanceWindowRequest.builder().active(false).build());

        var event = evaluationService.evaluate(rule.getId(), base.plusSeconds(61));

        assertTrue(event.isPresent());
        assertEquals(IncidentStatus.FIRING, event.get().status());
    }
}
