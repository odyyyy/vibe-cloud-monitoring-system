package com.monitoring.api;

import com.monitoring.api.dto.response.WorkspaceSummaryResponse;
import com.monitoring.api.service.WorkspaceSummaryService;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the Overview workspace counters against a real persistence context: the values
 * are derived only from the caller's applications within the 24 hour window.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:overview-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class WorkspaceSummaryServiceIntegrationTest {

    private static final long OWNER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final Instant WITHIN_WINDOW = NOW.minusSeconds(3600);
    private static final Instant OUTSIDE_WINDOW = NOW.minusSeconds(25 * 3600);

    @Autowired
    private WorkspaceSummaryService workspaceSummaryService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MetricSampleRepository metricSampleRepository;

    @Autowired
    private LogEventRepository logEventRepository;

    private Application application;

    @BeforeEach
    void setUp() {
        logEventRepository.deleteAll();
        metricSampleRepository.deleteAll();
        applicationRepository.deleteAll();
        application = applicationRepository.save(Application.builder()
                .name("overview-it-app")
                .apiKeyHash("app_overview_it")
                .organizationId(OWNER_ID)
                .build());
    }

    private void saveSample(Application app, String name, Instant timestamp) {
        metricSampleRepository.save(MetricSample.builder()
                .application(app)
                .name(name)
                .value(1.0)
                .timestamp(timestamp)
                .receivedAt(Instant.now())
                .tags(new HashMap<>())
                .build());
    }

    private void saveLog(Application app, Instant timestamp) {
        logEventRepository.save(LogEvent.builder()
                .application(app)
                .level(LogLevel.INFO)
                .message("event")
                .timestamp(timestamp)
                .receivedAt(Instant.now())
                .build());
    }

    @Test
    void emptyWorkspaceReturnsZeros() {
        applicationRepository.deleteAll();

        WorkspaceSummaryResponse summary = workspaceSummaryService.summarize(OWNER_ID, NOW);

        assertEquals(0L, summary.applicationCount());
        assertEquals(0L, summary.metricNameCount24h());
        assertEquals(0L, summary.logEventCount24h());
    }

    @Test
    void countsDistinctMetricNamesAndLogEventsWithinWindow() {
        saveSample(application, "cpu_usage", WITHIN_WINDOW);
        saveSample(application, "cpu_usage", WITHIN_WINDOW);
        saveSample(application, "mem_usage", WITHIN_WINDOW);
        saveLog(application, WITHIN_WINDOW);
        saveLog(application, WITHIN_WINDOW);
        Application second = applicationRepository.save(Application.builder()
                .name("overview-it-second")
                .apiKeyHash("app_overview_it_second")
                .organizationId(OWNER_ID)
                .build());
        saveSample(second, "disk_usage", WITHIN_WINDOW);
        saveLog(second, WITHIN_WINDOW);

        WorkspaceSummaryResponse summary = workspaceSummaryService.summarize(OWNER_ID, NOW);

        assertEquals(2L, summary.applicationCount());
        assertEquals(3L, summary.metricNameCount24h());
        assertEquals(3L, summary.logEventCount24h());
    }

    @Test
    void ignoresTelemetryOlderThanTheWindow() {
        saveSample(application, "cpu_usage", OUTSIDE_WINDOW);
        saveLog(application, OUTSIDE_WINDOW);

        WorkspaceSummaryResponse summary = workspaceSummaryService.summarize(OWNER_ID, NOW);

        assertEquals(1L, summary.applicationCount());
        assertEquals(0L, summary.metricNameCount24h());
        assertEquals(0L, summary.logEventCount24h());
    }

    @Test
    void excludesOtherTenantsTelemetry() {
        Application other = applicationRepository.save(Application.builder()
                .name("overview-it-other")
                .apiKeyHash("app_overview_it_other")
                .organizationId(2L)
                .build());
        saveSample(other, "cpu_usage", WITHIN_WINDOW);
        saveLog(other, WITHIN_WINDOW);

        WorkspaceSummaryResponse summary = workspaceSummaryService.summarize(OWNER_ID, NOW);

        assertEquals(1L, summary.applicationCount());
        assertEquals(0L, summary.metricNameCount24h());
        assertEquals(0L, summary.logEventCount24h());
    }
}
