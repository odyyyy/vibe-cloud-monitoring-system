package com.monitoring.api;

import com.monitoring.api.platform.PlatformMetricsService;
import com.monitoring.api.platform.PlatformResourceService;
import com.monitoring.collector.dto.request.MetricIngestRequest;
import com.monitoring.collector.dto.request.MetricPoint;
import com.monitoring.collector.dto.request.MetricsQueryRequest;
import com.monitoring.collector.dto.response.AggregatedMetricResponse;
import com.monitoring.collector.dto.request.MetricAggregation;
import com.monitoring.collector.service.MetricIngestService;
import com.monitoring.collector.service.MetricQueryService;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.dashboards.dto.request.UpdateDashboardRequest;
import com.monitoring.dashboards.dto.response.DashboardResponse;
import com.monitoring.dashboards.dto.response.WidgetResponse;
import com.monitoring.dashboards.entity.Dashboard;
import com.monitoring.dashboards.entity.WidgetType;
import com.monitoring.dashboards.exception.DashboardAccessDeniedException;
import com.monitoring.dashboards.service.DashboardService;
import com.monitoring.organization.entity.Organization;
import com.monitoring.organization.repository.MembershipRepository;
import com.monitoring.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the dogfooding bootstrap: a hidden System organization/application and a
 * platform-managed dashboard that every tenant can read but nobody can modify.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform-self-monitoring-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.platform-metrics.enabled=true",
        "monitoring.platform-metrics.initial-delay-ms=600000"
})
class PlatformSelfMonitoringIntegrationTest {

    private static final long ANY_ORGANIZATION_ID = 4242L;

    @Autowired
    private PlatformResourceService platformResourceService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private MetricIngestService metricIngestService;

    @Autowired
    private MetricQueryService metricQueryService;

    @Autowired
    private PlatformMetricsService platformMetricsService;

    @Test
    void bootstrapCreatesHiddenSystemResources() {
        Application application = platformResourceService.ensureSystemApplication();
        Organization organization = organizationRepository.findById(application.getOrganizationId())
                .orElseThrow();

        assertTrue(organization.isSystem());
        assertEquals(PlatformResourceService.SYSTEM_ORGANIZATION_NAME, organization.getName());
        assertTrue(application.isSystem());
        assertEquals(PlatformResourceService.SYSTEM_APPLICATION_NAME, application.getName());
        assertTrue(membershipRepository.findByOrganizationIdOrderByIdAsc(organization.getId()).isEmpty(),
                "System organization must not have human members");
    }

    @Test
    void platformDashboardHasExpectedWidgetsAndIsReadOnly() {
        Application application = platformResourceService.ensureSystemApplication();
        Dashboard dashboard = platformResourceService.ensurePlatformDashboard(application);

        assertTrue(dashboard.isSystem());
        assertEquals(PlatformResourceService.PLATFORM_DASHBOARD_NAME, dashboard.getName());

        DashboardResponse readable = dashboardService.getDashboard(ANY_ORGANIZATION_ID, dashboard.getId());
        assertTrue(Boolean.TRUE.equals(readable.getIsSystem()));
        assertEquals(4, readable.getWidgets().size());

        Map<WidgetType, Long> countsByType = readable.getWidgets().stream()
                .map(WidgetResponse::getType)
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()));
        assertEquals(2L, countsByType.get(WidgetType.GAUGE));
        assertEquals(2L, countsByType.get(WidgetType.METRIC_CHART));

        assertTrue(readable.getWidgets().stream()
                .anyMatch(widget -> widget.getConfig().contains("\"groupByTag\":\"status\"")));

        List<DashboardResponse> visible = dashboardService.listDashboards(ANY_ORGANIZATION_ID);
        assertTrue(visible.stream().anyMatch(candidate -> candidate.getId().equals(dashboard.getId())));

        assertThrows(DashboardAccessDeniedException.class, () -> dashboardService.updateDashboard(
                ANY_ORGANIZATION_ID,
                dashboard.getId(),
                UpdateDashboardRequest.builder().name("Hijacked").build()));
        assertThrows(DashboardAccessDeniedException.class,
                () -> dashboardService.deleteDashboard(ANY_ORGANIZATION_ID, dashboard.getId()));

        DashboardResponse stored = dashboardService.getDashboard(ANY_ORGANIZATION_ID, dashboard.getId());
        assertEquals(PlatformResourceService.PLATFORM_DASHBOARD_NAME, stored.getName());
        assertEquals(4, stored.getWidgets().size());
    }

    @Test
    void systemApplicationIsQueryableFromAnotherTenantButNotListedAsOwned() {
        Application systemApplication = platformResourceService.ensureSystemApplication();

        assertTrue(systemApplication.isSystem());
        assertFalse(applicationRepository.findByOrganizationId(ANY_ORGANIZATION_ID).stream()
                .anyMatch(application -> application.getId().equals(systemApplication.getId())));
        assertEquals(systemApplication.getId(),
                applicationService.getQueryableApplication(systemApplication.getId(), ANY_ORGANIZATION_ID).getId());
        assertThrows(ApplicationAccessDeniedException.class,
                () -> applicationService.getOwnedApplication(systemApplication.getId(), ANY_ORGANIZATION_ID));
    }

    @Test
    void systemTelemetryIsQueryableByAnyTenantThroughTheNormalQueryApi() {
        Application systemApplication = platformResourceService.ensureSystemApplication();
        Instant now = Instant.now();
        metricIngestService.ingest(systemApplication, MetricIngestRequest.builder()
                .metrics(List.of(
                        MetricPoint.builder()
                                .name(PlatformResourceService.INGEST_REQUESTS_METRIC)
                                .value(3.0)
                                .timestamp(now)
                                .tags(Map.of("source", "platform", "status", "200"))
                                .build(),
                        MetricPoint.builder()
                                .name(PlatformResourceService.INGEST_REQUESTS_METRIC)
                                .value(2.0)
                                .timestamp(now)
                                .tags(Map.of("source", "platform", "status", "500"))
                                .build()))
                .build());

        List<AggregatedMetricResponse> points = metricQueryService.query(ANY_ORGANIZATION_ID,
                MetricsQueryRequest.builder()
                        .applicationId(systemApplication.getId())
                        .metricName(PlatformResourceService.INGEST_REQUESTS_METRIC)
                        .from(now.minus(Duration.ofMinutes(5)))
                        .to(now.plus(Duration.ofMinutes(1)))
                        .aggregation(MetricAggregation.SUM)
                        .groupByTag("status")
                        .intervalMinutes(1)
                        .build());

        assertEquals(Map.of("200", 3.0, "500", 2.0), points.stream()
                .collect(Collectors.toMap(AggregatedMetricResponse::getGroup,
                        AggregatedMetricResponse::getValue)));
    }

    @Test
    void publishStoresRealJvmAndDatabasePoolMetrics() {
        Application systemApplication = platformResourceService.ensureSystemApplication();
        Instant now = Instant.now();

        platformMetricsService.publish();

        assertFalse(queryMetric(systemApplication, PlatformResourceService.JVM_HEAP_METRIC, now).isEmpty(),
                "JVM heap metrics should be collected from the running JVM");
        assertFalse(queryMetric(systemApplication, PlatformResourceService.DB_CONNECTIONS_METRIC, now).isEmpty(),
                "HikariCP pool metrics should be bound under their Micrometer dotted name");
    }

    private List<AggregatedMetricResponse> queryMetric(Application application, String metricName, Instant now) {
        return metricQueryService.query(ANY_ORGANIZATION_ID, MetricsQueryRequest.builder()
                .applicationId(application.getId())
                .metricName(metricName)
                .from(now.minus(Duration.ofMinutes(5)))
                .to(now.plus(Duration.ofMinutes(1)))
                .aggregation(MetricAggregation.MAX)
                .intervalMinutes(1)
                .build());
    }
}
