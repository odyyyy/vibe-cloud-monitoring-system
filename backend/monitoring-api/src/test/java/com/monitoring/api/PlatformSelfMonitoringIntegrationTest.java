package com.monitoring.api;

import com.monitoring.api.platform.PlatformMetricsService;
import com.monitoring.api.platform.PlatformResourceService;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.auth.entity.Role;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.auth.security.PlatformAdminAccess;
import com.monitoring.collector.dto.request.MetricAggregation;
import com.monitoring.collector.dto.request.MetricIngestRequest;
import com.monitoring.collector.dto.request.MetricPoint;
import com.monitoring.collector.dto.request.MetricsQueryRequest;
import com.monitoring.collector.dto.response.AggregatedMetricResponse;
import com.monitoring.collector.service.MetricIngestService;
import com.monitoring.collector.service.MetricQueryService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the dogfooding bootstrap: a hidden System organization/application and a
 * platform-managed dashboard that only PLATFORM_ADMIN can reach or modify.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform-self-monitoring-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.platform-metrics.enabled=true",
        "monitoring.platform-metrics.initial-delay-ms=600000"
})
@AutoConfigureMockMvc
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

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
    void platformDashboardIsHiddenFromTenantsAndReadableByPlatformAdmin() {
        Application application = platformResourceService.ensureSystemApplication();
        Dashboard dashboard = platformResourceService.ensurePlatformDashboard(application);

        assertTrue(dashboard.isSystem());
        assertEquals(PlatformResourceService.PLATFORM_HEALTH_DASHBOARD_TYPE, dashboard.getSystemDashboardType());
        assertEquals(PlatformResourceService.PLATFORM_DASHBOARD_NAME, dashboard.getName());

        DashboardResponse readable = dashboardService.getSystemDashboard(
                PlatformResourceService.PLATFORM_HEALTH_DASHBOARD_TYPE);
        assertTrue(Boolean.TRUE.equals(readable.getIsSystem()));
        assertEquals(PlatformResourceService.PLATFORM_HEALTH_DASHBOARD_TYPE, readable.getSystemDashboardType());
        assertEquals(4, readable.getWidgets().size());

        Map<WidgetType, Long> countsByType = readable.getWidgets().stream()
                .map(WidgetResponse::getType)
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()));
        assertEquals(2L, countsByType.get(WidgetType.GAUGE));
        assertEquals(2L, countsByType.get(WidgetType.METRIC_CHART));

        assertTrue(readable.getWidgets().stream()
                .anyMatch(widget -> widget.getConfig().contains("\"groupByTag\":\"status\"")));

        List<DashboardResponse> visible = dashboardService.listDashboards(ANY_ORGANIZATION_ID);
        assertFalse(visible.stream().anyMatch(candidate -> candidate.getId().equals(dashboard.getId())),
                "Regular dashboard lists must not contain platform dashboards");

        assertThrows(DashboardAccessDeniedException.class,
                () -> dashboardService.getDashboard(ANY_ORGANIZATION_ID, dashboard.getId()));
        assertThrows(DashboardAccessDeniedException.class, () -> dashboardService.updateDashboard(
                ANY_ORGANIZATION_ID,
                dashboard.getId(),
                UpdateDashboardRequest.builder().name("Hijacked").build(),
                false));
        assertThrows(DashboardAccessDeniedException.class,
                () -> dashboardService.deleteDashboard(ANY_ORGANIZATION_ID, dashboard.getId(), false));
        assertThrows(DashboardAccessDeniedException.class,
                () -> dashboardService.generateShareToken(ANY_ORGANIZATION_ID, dashboard.getId()));

        DashboardResponse updated = dashboardService.updateDashboard(
                ANY_ORGANIZATION_ID,
                dashboard.getId(),
                UpdateDashboardRequest.builder().name(PlatformResourceService.PLATFORM_DASHBOARD_NAME).build(),
                true);
        assertEquals(PlatformResourceService.PLATFORM_DASHBOARD_NAME, updated.getName());
    }

    @Test
    void platformHealthEndpointRequiresPlatformAdmin() throws Exception {
        platformResourceService.ensureSystemApplication();
        platformResourceService.ensurePlatformDashboard(platformResourceService.ensureSystemApplication());

        User regular = createUser("regular-user", Role.USER);
        User platformAdmin = createUser("platform-admin", Role.PLATFORM_ADMIN);

        mockMvc.perform(get("/api/v1/admin/platform-health"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/platform-health")
                        .header(HttpHeaders.AUTHORIZATION, bearer(regular.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/platform-health")
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformAdmin.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(PlatformResourceService.PLATFORM_DASHBOARD_NAME))
                .andExpect(jsonPath("$.isSystem").value(true))
                .andExpect(jsonPath("$.systemDashboardType")
                        .value(PlatformResourceService.PLATFORM_HEALTH_DASHBOARD_TYPE))
                .andExpect(jsonPath("$.widgets", hasSize(4)));
    }

    @Test
    void systemApplicationIsNotQueryableByRegularTenants() {
        Application systemApplication = platformResourceService.ensureSystemApplication();

        assertTrue(systemApplication.isSystem());
        assertFalse(applicationRepository.findByOrganizationId(ANY_ORGANIZATION_ID).stream()
                .anyMatch(application -> application.getId().equals(systemApplication.getId())));
        assertThrows(ApplicationAccessDeniedException.class,
                () -> applicationService.getQueryableApplication(systemApplication.getId(), ANY_ORGANIZATION_ID));
        assertThrows(ApplicationAccessDeniedException.class,
                () -> applicationService.getOwnedApplication(systemApplication.getId(), ANY_ORGANIZATION_ID));
        assertEquals(systemApplication.getId(),
                applicationService.getQueryableApplication(
                        systemApplication.getId(), ANY_ORGANIZATION_ID, true).getId());
    }

    @Test
    void systemTelemetryRequiresPlatformAdmin() {
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

        MetricsQueryRequest request = MetricsQueryRequest.builder()
                .applicationId(systemApplication.getId())
                .metricName(PlatformResourceService.INGEST_REQUESTS_METRIC)
                .from(now.minus(Duration.ofMinutes(5)))
                .to(now.plus(Duration.ofMinutes(1)))
                .aggregation(MetricAggregation.SUM)
                .groupByTag("status")
                .intervalMinutes(1)
                .build();

        assertThrows(ApplicationAccessDeniedException.class,
                () -> metricQueryService.query(ANY_ORGANIZATION_ID, request));

        List<AggregatedMetricResponse> points = asPlatformAdmin(
                () -> metricQueryService.query(ANY_ORGANIZATION_ID, request));

        assertEquals(Map.of("200", 3.0, "500", 2.0), points.stream()
                .collect(Collectors.toMap(AggregatedMetricResponse::getGroup,
                        AggregatedMetricResponse::getValue)));
    }

    @Test
    void publishStoresRealJvmAndDatabasePoolMetrics() {
        Application systemApplication = platformResourceService.ensureSystemApplication();
        Instant now = Instant.now();

        platformMetricsService.publish();

        assertFalse(asPlatformAdmin(
                        () -> queryMetric(systemApplication, PlatformResourceService.JVM_HEAP_METRIC, now)).isEmpty(),
                "JVM heap metrics should be collected from the running JVM");
        assertFalse(asPlatformAdmin(
                        () -> queryMetric(systemApplication, PlatformResourceService.DB_CONNECTIONS_METRIC, now)).isEmpty(),
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

    private User createUser(String username, Role role) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("encoded-secret")
                .emailVerified(true)
                .role(role)
                .build());
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "user-" + userId);
    }

    private <T> T asPlatformAdmin(Supplier<T> action) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority(PlatformAdminAccess.AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
