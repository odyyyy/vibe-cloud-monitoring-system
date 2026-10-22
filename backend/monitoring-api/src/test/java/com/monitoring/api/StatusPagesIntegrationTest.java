package com.monitoring.api;

import com.monitoring.healthcheck.dto.request.CreateHealthCheckRequest;
import com.monitoring.healthcheck.dto.response.HealthCheckResponse;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import com.monitoring.healthcheck.repository.HealthCheckResultRepository;
import com.monitoring.healthcheck.service.HealthCheckOutcome;
import com.monitoring.healthcheck.service.HealthCheckService;
import com.monitoring.statuspage.dto.request.CreateStatusPageRequest;
import com.monitoring.statuspage.dto.request.StatusPageComponentRequest;
import com.monitoring.statuspage.dto.request.UpdateStatusPageRequest;
import com.monitoring.statuspage.dto.response.PublicStatusPageResponse;
import com.monitoring.statuspage.dto.response.StatusPageResponse;
import com.monitoring.statuspage.entity.ComponentStatus;
import com.monitoring.statuspage.exception.InvalidStatusPageException;
import com.monitoring.statuspage.exception.StatusPageAccessDeniedException;
import com.monitoring.statuspage.exception.StatusPageLimitExceededException;
import com.monitoring.statuspage.exception.StatusPageNotFoundException;
import com.monitoring.statuspage.exception.StatusPageSlugConflictException;
import com.monitoring.statuspage.repository.StatusPageRepository;
import com.monitoring.statuspage.service.PublicStatusPageService;
import com.monitoring.statuspage.service.StatusPageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:statuspages-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.healthcheck.scheduler.enabled=false",
        "monitoring.alerts.scheduler.enabled=false",
        "monitoring.status-pages.public.cache-ttl-seconds=0",
        "monitoring.status-pages.public.max-requests-per-minute=100000"
})
@AutoConfigureMockMvc
class StatusPagesIntegrationTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final String PUBLIC_URL = "http://8.8.8.8/health";

    @Autowired
    private StatusPageService statusPageService;

    @Autowired
    private PublicStatusPageService publicStatusPageService;

    @Autowired
    private StatusPageRepository statusPageRepository;

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private HealthCheckConfigRepository healthCheckConfigRepository;

    @Autowired
    private HealthCheckResultRepository healthCheckResultRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        statusPageRepository.deleteAll();
        healthCheckResultRepository.deleteAll();
        healthCheckConfigRepository.deleteAll();
    }

    private HealthCheckResponse createCheck(long organizationId, String name) {
        return healthCheckService.create(organizationId, CreateHealthCheckRequest.builder()
                .name(name)
                .targetUrl(PUBLIC_URL)
                .method(HealthCheckMethod.GET)
                .expectedStatusCode(200)
                .intervalSeconds(30)
                .active(true)
                .build());
    }

    private void record(long configId, HealthStatus status, Instant at) {
        healthCheckService.recordResult(configId, new HealthCheckOutcome(status, 25, null), at);
    }

    private StatusPageResponse createPage(long organizationId,
                                          String slug,
                                          boolean active,
                                          List<StatusPageComponentRequest> components) {
        return statusPageService.create(organizationId, CreateStatusPageRequest.builder()
                .slug(slug)
                .title("Acme Status")
                .description("Current availability")
                .active(active)
                .components(components)
                .build());
    }

    private StatusPageComponentRequest component(String name, Long healthCheckId) {
        return StatusPageComponentRequest.builder()
                .name(name)
                .linkedHealthCheckId(healthCheckId)
                .build();
    }

    @Test
    void publicResponseAggregatesComponentStatuses() {
        HealthCheckResponse up = createCheck(OWNER_ID, "API");
        HealthCheckResponse down = createCheck(OWNER_ID, "Dashboard");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        record(up.getId(), HealthStatus.UP, now.minusSeconds(30));
        record(down.getId(), HealthStatus.DOWN, now.minusSeconds(30));

        createPage(OWNER_ID, "test-status", true, List.of(
                component("API", up.getId()),
                component("Dashboard", down.getId()),
                component("Workers", null)));

        PublicStatusPageResponse response = publicStatusPageService.getBySlug("test-status");

        assertEquals(ComponentStatus.DOWN, response.getOverallStatus());
        assertEquals("Acme Status", response.getTitle());
        assertEquals(3, response.getComponents().size());
        assertEquals(ComponentStatus.UP, response.getComponents().get(0).getStatus());
        assertEquals(ComponentStatus.DOWN, response.getComponents().get(1).getStatus());
        assertEquals(ComponentStatus.UNKNOWN, response.getComponents().get(2).getStatus());
    }

    @Test
    void recentFailureMarksComponentDegradedUntilLatestResultIsHealthy() {
        HealthCheckResponse check = createCheck(OWNER_ID, "API");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        record(check.getId(), HealthStatus.UP, now.minusSeconds(120));
        record(check.getId(), HealthStatus.DOWN, now.minusSeconds(90));
        record(check.getId(), HealthStatus.UP, now.minusSeconds(60));

        createPage(OWNER_ID, "degraded-status", true, List.of(component("API", check.getId())));

        PublicStatusPageResponse response = publicStatusPageService.getBySlug("degraded-status");

        assertEquals(ComponentStatus.DEGRADED, response.getOverallStatus());
        assertEquals(ComponentStatus.DEGRADED, response.getComponents().get(0).getStatus());
    }

    @Test
    void inactiveOrMissingPageIsNotFound() {
        createPage(OWNER_ID, "inactive-status", false, List.of());

        assertThrows(StatusPageNotFoundException.class,
                () -> publicStatusPageService.getBySlug("inactive-status"));
        assertThrows(StatusPageNotFoundException.class,
                () -> publicStatusPageService.getBySlug("missing-status"));
    }

    @Test
    void recentOutagesAreDerivedFromResultTransitions() {
        HealthCheckResponse check = createCheck(OWNER_ID, "API");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        record(check.getId(), HealthStatus.UP, now.minusSeconds(1200));
        record(check.getId(), HealthStatus.DOWN, now.minusSeconds(900));
        record(check.getId(), HealthStatus.UP, now.minusSeconds(600));
        record(check.getId(), HealthStatus.DOWN, now.minusSeconds(300));

        createPage(OWNER_ID, "outage-status", true, List.of(component("API", check.getId())));

        PublicStatusPageResponse response = publicStatusPageService.getBySlug("outage-status");

        assertEquals(2, response.getRecentOutages().size());
        assertNull(response.getRecentOutages().get(0).getResolvedAt());
        assertEquals("API was unavailable", response.getRecentOutages().get(0).getTitle());
        assertEquals(now.minusSeconds(300), response.getRecentOutages().get(0).getStartedAt());
        assertEquals(now.minusSeconds(600), response.getRecentOutages().get(1).getResolvedAt());
    }

    @Test
    void anotherOrganizationCannotReadOrModifyThePage() {
        StatusPageResponse page = createPage(OWNER_ID, "private-status", true, List.of());

        assertEquals(0, statusPageService.list(OTHER_ID).size());
        assertThrows(StatusPageAccessDeniedException.class,
                () -> statusPageService.get(OTHER_ID, page.getId()));
        assertThrows(StatusPageAccessDeniedException.class,
                () -> statusPageService.update(OTHER_ID, page.getId(), UpdateStatusPageRequest.builder()
                        .slug("private-status")
                        .title("Hijacked")
                        .build()));
        assertThrows(StatusPageAccessDeniedException.class,
                () -> statusPageService.delete(OTHER_ID, page.getId()));
    }

    @Test
    void linkedHealthCheckMustBelongToTheSameOrganization() {
        HealthCheckResponse foreign = createCheck(OTHER_ID, "Foreign API");

        assertThrows(InvalidStatusPageException.class,
                () -> createPage(OWNER_ID, "foreign-link", true,
                        List.of(component("API", foreign.getId()))));
    }

    @Test
    void rejectsDuplicateReservedSlugsAndPageLimit() {
        createPage(OWNER_ID, "test-status", true, List.of());
        assertThrows(StatusPageSlugConflictException.class,
                () -> createPage(OWNER_ID, "test-status", true, List.of()));
        assertThrows(InvalidStatusPageException.class,
                () -> createPage(OWNER_ID, "admin", true, List.of()));

        createPage(OWNER_ID, "second-status", true, List.of());
        createPage(OWNER_ID, "third-status", true, List.of());
        assertThrows(StatusPageLimitExceededException.class,
                () -> createPage(OWNER_ID, "fourth-status", true, List.of()));
    }

    @Test
    void publicEndpointNeedsNoAuthenticationAndReturnsOnlyPublicFields() throws Exception {
        StatusPageResponse page = createPage(OWNER_ID, "public-status", true, List.of());

        mockMvc.perform(get("/api/v1/public/status-pages/{slug}", page.getSlug()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=30")))
                .andExpect(jsonPath("$.title").value("Acme Status"))
                .andExpect(jsonPath("$.overallStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.components").isArray())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.components[0].linkedHealthCheckId").doesNotExist());
    }

    @Test
    void inactivePageReturns404FromThePublicEndpoint() throws Exception {
        createPage(OWNER_ID, "inactive-public", false, List.of());

        mockMvc.perform(get("/api/v1/public/status-pages/{slug}", "inactive-public"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateReplacesComponentsAndCanDeactivatePage() {
        HealthCheckResponse first = createCheck(OWNER_ID, "First");
        HealthCheckResponse second = createCheck(OWNER_ID, "Second");
        StatusPageResponse page = createPage(OWNER_ID, "edit-status", true,
                List.of(component("First", first.getId())));

        StatusPageResponse updated = statusPageService.update(OWNER_ID, page.getId(),
                UpdateStatusPageRequest.builder()
                        .slug("edit-status")
                        .title("Renamed")
                        .description("Updated")
                        .active(false)
                        .components(List.of(
                                component("Second", second.getId()),
                                component("First", first.getId())))
                        .build());

        assertEquals("Renamed", updated.getTitle());
        assertEquals(2, updated.getComponents().size());
        assertEquals("Second", updated.getComponents().get(0).getName());
        assertEquals(0, updated.getComponents().get(0).getSortOrder());
        assertEquals(1, updated.getComponents().get(1).getSortOrder());
        assertTrue(!updated.isActive());
        assertThrows(StatusPageNotFoundException.class,
                () -> publicStatusPageService.getBySlug("edit-status"));
    }
}
