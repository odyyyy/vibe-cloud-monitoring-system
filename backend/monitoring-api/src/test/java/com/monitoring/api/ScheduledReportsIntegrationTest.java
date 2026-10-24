package com.monitoring.api;

import com.monitoring.alerts.dto.request.ScheduledReportRequest;
import com.monitoring.alerts.dto.response.ScheduledReportPreviewResponse;
import com.monitoring.alerts.dto.response.ScheduledReportResponse;
import com.monitoring.alerts.entity.NotificationDeliveryStatus;
import com.monitoring.alerts.entity.ScheduledReportChannelType;
import com.monitoring.alerts.entity.ScheduledReportFrequency;
import com.monitoring.alerts.exception.ScheduledReportAccessDeniedException;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.alerts.repository.NotificationDeliveryRepository;
import com.monitoring.alerts.repository.ScheduledReportRepository;
import com.monitoring.alerts.service.ScheduledReportService;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthCheckResult;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import com.monitoring.healthcheck.repository.HealthCheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scheduled-reports-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.healthcheck.scheduler.enabled=false",
        "monitoring.alerts.scheduler.enabled=false"
})
class ScheduledReportsIntegrationTest {

    private static final Long ORGANIZATION_ID = 7L;
    private Instant now;

    @Autowired
    private ScheduledReportService reportService;

    @Autowired
    private ScheduledReportRepository reportRepository;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private MetricSampleRepository metricSampleRepository;

    @Autowired
    private HealthCheckConfigRepository healthCheckConfigRepository;

    @Autowired
    private HealthCheckResultRepository healthCheckResultRepository;

    @Autowired
    private AlertIncidentRepository incidentRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        deliveryRepository.deleteAll();
        reportRepository.deleteAll();
        logEventRepository.deleteAll();
        metricSampleRepository.deleteAll();
        healthCheckResultRepository.deleteAll();
        healthCheckConfigRepository.deleteAll();
        incidentRepository.deleteAll();
        alertRuleRepository.deleteAll();
        applicationRepository.deleteAll();
    }

    @Test
    void previewAggregatesTenantDataAndEscapesUntrustedText() {
        Application application = applicationRepository.save(Application.builder()
                .name("checkout")
                .apiKeyHash(UUID.randomUUID().toString().replace("-", ""))
                .organizationId(ORGANIZATION_ID)
                .build());
        logEventRepository.save(log(application, LogLevel.ERROR, "<script>alert(1)</script> DB down", now.minusSeconds(60)));
        logEventRepository.save(log(application, LogLevel.ERROR, "<script>alert(1)</script> DB down", now.minusSeconds(120)));
        logEventRepository.save(log(application, LogLevel.INFO, "healthy", now.minusSeconds(180)));
        metricSampleRepository.save(metric(application, "system.cpu.usage", 42.0, now.minusSeconds(60)));
        metricSampleRepository.save(metric(application, "system.cpu.usage", 55.0, now.minusSeconds(120)));

        HealthCheckConfig healthCheck = healthCheckConfigRepository.save(HealthCheckConfig.builder()
                .organizationId(ORGANIZATION_ID)
                .name("API")
                .targetUrl("https://example.com/health")
                .method(HealthCheckMethod.GET)
                .expectedStatusCode(200)
                .intervalSeconds(60)
                .active(true)
                .lastCheckedAt(now)
                .build());
        healthCheckResultRepository.save(HealthCheckResult.builder()
                .config(healthCheck)
                .status(HealthStatus.UP)
                .responseTimeMs(20)
                .checkedAt(now.minusSeconds(60))
                .build());
        healthCheckResultRepository.save(HealthCheckResult.builder()
                .config(healthCheck)
                .status(HealthStatus.DOWN)
                .responseTimeMs(500)
                .checkedAt(now.minusSeconds(120))
                .build());

        ScheduledReportResponse report = reportService.create(ORGANIZATION_ID, request());

        ScheduledReportPreviewResponse preview = reportService.sendTest(ORGANIZATION_ID, report.getId());

        assertEquals(1, preview.getContent().applicationCount());
        assertEquals(1, preview.getContent().healthCheckCount());
        assertEquals(2, preview.getContent().uptime().totalChecks());
        assertEquals(1, preview.getContent().uptime().upChecks());
        assertEquals(50.0, preview.getContent().uptime().percentage());
        assertEquals(2, preview.getContent().errors().totalErrors());
        assertEquals("<script>alert(1)</script> DB down",
                preview.getContent().errors().topErrors().get(0).message());
        assertEquals(2, preview.getContent().metrics().totalPoints());
        assertEquals(1, preview.getContent().metrics().distinctMetrics());
        assertEquals(NotificationDeliveryStatus.FAILED, preview.getDeliveryStatus());
        assertTrue(preview.getHtml().contains("&lt;script&gt;alert(1)&lt;/script&gt; DB down"));
        assertFalse(preview.getHtml().contains("<script>alert(1)</script>"));
    }

    @Test
    void reportsAreTenantScoped() {
        ScheduledReportResponse report = reportService.create(ORGANIZATION_ID, request());

        assertThrows(ScheduledReportAccessDeniedException.class,
                () -> reportService.get(8L, report.getId()));
        assertTrue(reportService.list(8L).isEmpty());
        assertEquals(1, reportService.list(ORGANIZATION_ID).size());
    }

    private ScheduledReportRequest request() {
        return ScheduledReportRequest.builder()
                .name("Daily digest")
                .frequency(ScheduledReportFrequency.DAILY)
                .timeOfDay(LocalTime.of(9, 0))
                .channelType(ScheduledReportChannelType.EMAIL)
                .recipients(List.of("ops@example.com"))
                .includeMetrics(true)
                .includeTopErrors(true)
                .includeUptime(true)
                .build();
    }

    private LogEvent log(Application application, LogLevel level, String message, Instant timestamp) {
        return LogEvent.builder()
                .application(application)
                .level(level)
                .message(message)
                .timestamp(timestamp)
                .receivedAt(timestamp)
                .build();
    }

    private MetricSample metric(Application application, String name, double value, Instant timestamp) {
        return MetricSample.builder()
                .application(application)
                .name(name)
                .value(value)
                .timestamp(timestamp)
                .receivedAt(timestamp)
                .build();
    }
}
