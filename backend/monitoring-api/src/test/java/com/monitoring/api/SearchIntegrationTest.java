package com.monitoring.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.auth.entity.Role;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.auth.service.EmailSender;
import com.monitoring.collector.entity.SavedLogView;
import com.monitoring.collector.repository.SavedLogViewRepository;
import com.monitoring.dashboards.entity.Dashboard;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import com.monitoring.organization.dto.response.OrganizationResponse;
import com.monitoring.organization.entity.Membership;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.repository.MembershipRepository;
import com.monitoring.organization.repository.OrganizationRepository;
import com.monitoring.organization.service.OrganizationService;
import com.monitoring.organization.tenant.CurrentTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the global search endpoint: tenant isolation, system-dashboard
 * exclusion, literal wildcard handling, per-type limits, validation and rate limiting.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:search-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.search.rate-limit.max-requests=5",
        "monitoring.search.rate-limit.window-seconds=60"
})
@AutoConfigureMockMvc
class SearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private HealthCheckConfigRepository healthCheckConfigRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertIncidentRepository alertIncidentRepository;

    @Autowired
    private SavedLogViewRepository savedLogViewRepository;

    @MockBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        alertIncidentRepository.deleteAll();
        alertRuleRepository.deleteAll();
        healthCheckConfigRepository.deleteAll();
        savedLogViewRepository.deleteAll();
        dashboardRepository.deleteAll();
        applicationRepository.deleteAll();
        membershipRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void viewerSearchesOnlyOwnTenantResources() throws Exception {
        Account owner = createAccount("search-owner", Role.USER);
        User viewer = createUser("search-viewer", Role.USER);
        membershipRepository.save(Membership.builder()
                .organizationId(owner.organizationId())
                .userId(viewer.getId())
                .role(MembershipRole.VIEWER)
                .build());
        Account other = createAccount("search-other", Role.USER);

        createApplication(owner.organizationId(), "Payments app");
        createDashboard(owner.organizationId(), "Payments board", false, null);
        createHealthCheck(owner.organizationId(), "Payments health");
        AlertRule rule = createAlertRule(owner.organizationId(), "Payments alert");
        createIncident(owner.organizationId(), rule);
        createSavedView(owner.organizationId(), "Payments view");

        createApplication(other.organizationId(), "Payments secret");
        createDashboard(other.organizationId(), "Payments secret board", false, null);

        JsonNode body = search(viewer, owner.organizationId(), Map.of("q", "payments"));

        List<String> titles = titles(body);
        assertThat(titles)
                .contains("Payments app", "Payments board", "Payments health",
                        "Payments alert", "Payments view")
                .doesNotContain("Payments secret", "Payments secret board");
        assertThat(types(body)).contains(
                "applications", "dashboards", "healthChecks", "alertRules", "savedViews", "incidents");
    }

    @Test
    void systemDashboardsAreExcludedEvenForPlatformAdmins() throws Exception {
        Account admin = createAccount("search-platform-admin", Role.PLATFORM_ADMIN);
        createDashboard(admin.organizationId(), "Payments platform dashboard", true, "PLATFORM_HEALTH");
        createDashboard(admin.organizationId(), "Payments customer dashboard", false, null);

        JsonNode body = search(admin.user(), admin.organizationId(), Map.of("q", "payments"));

        assertThat(titles(body))
                .contains("Payments customer dashboard")
                .doesNotContain("Payments platform dashboard");
    }

    @Test
    void wildcardCharactersAreTreatedLiterally() throws Exception {
        Account owner = createAccount("search-wildcards", Role.USER);
        createApplication(owner.organizationId(), "100% coverage");
        createApplication(owner.organizationId(), "100X coverage");

        JsonNode wildcardMiss = search(owner.user(), owner.organizationId(), Map.of("q", "%x"));
        assertThat(titles(wildcardMiss)).isEmpty();

        JsonNode literalMatch = search(owner.user(), owner.organizationId(), Map.of("q", "% c"));
        assertThat(titles(literalMatch)).containsExactly("100% coverage");
    }

    @Test
    void perTypeLimitIsEnforced() throws Exception {
        Account owner = createAccount("search-limit", Role.USER);
        createApplication(owner.organizationId(), "Limit app one");
        createApplication(owner.organizationId(), "Limit app two");
        createApplication(owner.organizationId(), "Limit app three");

        JsonNode body = search(owner.user(), owner.organizationId(), Map.of(
                "q", "limit",
                "types", "applications",
                "limit", "2"));

        assertThat(titles(body)).hasSize(2);
    }

    @Test
    void invalidSearchParametersAreRejectedBeforeRateLimiting() throws Exception {
        Account owner = createAccount("search-validation", Role.USER);

        expectBadRequest(owner.user(), owner.organizationId(), Map.of());
        expectBadRequest(owner.user(), owner.organizationId(), Map.of("q", "a"));
        expectBadRequest(owner.user(), owner.organizationId(), Map.of("q", "x".repeat(65)));
        expectBadRequest(owner.user(), owner.organizationId(), Map.of("q", "valid", "types", "logs"));
        expectBadRequest(owner.user(), owner.organizationId(), Map.of("q", "valid", "limit", "0"));
        expectBadRequest(owner.user(), owner.organizationId(), Map.of("q", "valid", "limit", "11"));
    }

    @Test
    void searchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "payments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchIsRateLimitedPerUser() throws Exception {
        Account owner = createAccount("search-rate-limit", Role.USER);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/search")
                            .param("q", "payments")
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner.user()))
                            .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private JsonNode search(User user, Long organizationId, Map<String, String> params) throws Exception {
        var request = get("/api/v1/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header(CurrentTenantService.ORGANIZATION_HEADER, organizationId);
        params.forEach(request::param);
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void expectBadRequest(User user, Long organizationId, Map<String, String> params) throws Exception {
        var request = get("/api/v1/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header(CurrentTenantService.ORGANIZATION_HEADER, organizationId);
        params.forEach(request::param);
        mockMvc.perform(request).andExpect(status().isBadRequest());
    }

    private List<String> titles(JsonNode body) {
        List<String> titles = new ArrayList<>();
        body.path("results").forEach(result -> titles.add(result.path("title").asText()));
        return titles;
    }

    private List<String> types(JsonNode body) {
        List<String> types = new ArrayList<>();
        body.path("results").forEach(result -> types.add(result.path("type").asText()));
        return types;
    }

    private Account createAccount(String username, Role role) {
        User user = createUser(username, role);
        OrganizationResponse organization = organizationService.createPersonal(user.getId(), username);
        return new Account(user, organization.id());
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

    private void createApplication(Long organizationId, String name) {
        applicationRepository.save(Application.builder()
                .name(name)
                .apiKeyHash(UUID.randomUUID().toString())
                .apiKeyHint("app_…" + UUID.randomUUID().toString().substring(0, 4))
                .organizationId(organizationId)
                .isSystem(false)
                .build());
    }

    private Dashboard createDashboard(Long organizationId,
                                      String name,
                                      boolean system,
                                      String systemType) {
        return dashboardRepository.save(Dashboard.builder()
                .name(name)
                .organizationId(organizationId)
                .isSystem(system)
                .systemDashboardType(systemType)
                .build());
    }

    private void createHealthCheck(Long organizationId, String name) {
        healthCheckConfigRepository.save(HealthCheckConfig.builder()
                .organizationId(organizationId)
                .name(name)
                .targetUrl("https://example.com/health")
                .method(HealthCheckMethod.GET)
                .expectedStatusCode(200)
                .intervalSeconds(60)
                .active(true)
                .build());
    }

    private AlertRule createAlertRule(Long organizationId, String name) {
        return alertRuleRepository.save(AlertRule.builder()
                .organizationId(organizationId)
                .name(name)
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target("cpu")
                .condition(AlertCondition.GREATER_THAN)
                .threshold(80)
                .durationMinutes(5)
                .active(true)
                .build());
    }

    private void createIncident(Long organizationId, AlertRule rule) {
        alertIncidentRepository.save(AlertIncident.builder()
                .organizationId(organizationId)
                .rule(rule)
                .status(IncidentStatus.FIRING)
                .triggeredAt(Instant.now())
                .build());
    }

    private void createSavedView(Long organizationId, String name) {
        savedLogViewRepository.save(SavedLogView.builder()
                .organizationId(organizationId)
                .name(name)
                .queryParams("{}")
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateToken(user.getId(), "user-" + user.getId());
    }

    private record Account(User user, Long organizationId) {
    }
}
