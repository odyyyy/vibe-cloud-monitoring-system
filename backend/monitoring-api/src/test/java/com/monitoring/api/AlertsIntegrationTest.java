package com.monitoring.api;

import com.monitoring.alerts.dto.AlertIncidentResponse;
import com.monitoring.alerts.dto.AlertRuleResponse;
import com.monitoring.alerts.dto.CreateAlertRuleRequest;
import com.monitoring.alerts.dto.CreateNotificationChannelRequest;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.exception.AlertRuleAccessDeniedException;
import com.monitoring.alerts.exception.InvalidWebhookUrlException;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.alerts.service.AlertEvaluationService;
import com.monitoring.alerts.service.AlertService;
import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.healthcheck.dto.CreateHealthCheckRequest;
import com.monitoring.healthcheck.dto.HealthCheckResponse;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import com.monitoring.healthcheck.repository.HealthCheckResultRepository;
import com.monitoring.healthcheck.service.HealthCheckOutcome;
import com.monitoring.healthcheck.service.HealthCheckService;
import com.monitoring.storage.dto.ApplicationCreateRequest;
import com.monitoring.storage.dto.ApplicationWithKeyResponse;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.ApplicationRepository;
import com.monitoring.storage.service.ApplicationService;
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
    private HealthCheckService healthCheckService;

    @Autowired
    private HealthCheckConfigRepository healthCheckConfigRepository;

    @Autowired
    private HealthCheckResultRepository healthCheckResultRepository;

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll();
        ruleRepository.deleteAll();
        channelRepository.deleteAll();
        metricSampleRepository.deleteAll();
        healthCheckResultRepository.deleteAll();
        healthCheckConfigRepository.deleteAll();
        applicationRepository.deleteAll();
    }

    private Application createApplication(long ownerId, String name) {
        ApplicationCreateRequest request = new ApplicationCreateRequest();
        request.setName(name);
        ApplicationWithKeyResponse created = applicationService.createApplication(request, ownerId);
        return applicationRepository.findById(created.id()).orElseThrow();
    }

    private AlertRuleResponse createMetricRule(long ownerId, long applicationId) {
        return alertService.createRule(ownerId, CreateAlertRuleRequest.builder()
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
                        com.monitoring.alerts.dto.UpdateAlertRuleRequest.builder().name("x").build()));
        assertThrows(AlertRuleAccessDeniedException.class,
                () -> alertService.deleteRule(OTHER_ID, rule.getId()));
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
}
