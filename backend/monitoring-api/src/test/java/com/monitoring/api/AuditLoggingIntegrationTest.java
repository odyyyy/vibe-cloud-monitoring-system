package com.monitoring.api;

import com.monitoring.audit.entity.AuditEvent;
import com.monitoring.audit.repository.AuditEventRepository;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.dashboards.entity.Dashboard;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the audit trail: sensitive controller actions produce events, the
 * read endpoint is scoped to the authenticated user and immutable, and client metadata is
 * captured. Runs against the real persistence context so the V12 migration and Hibernate
 * mapping are validated too.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:audit-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class AuditLoggingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private DashboardRepository dashboardRepository;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
        dashboardRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void regeneratingApiKeyCreatesAuditEvent() throws Exception {
        User user = createUser("ada");
        Application application = createApplication(user.getId());

        mockMvc.perform(post("/api/applications/{id}/regenerate-api-key", application.getId())
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk());

        List<AuditEvent> events = auditEventRepository.findAll();
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getAction()).isEqualTo("API_KEY_REGENERATED");
        assertThat(event.getUserId()).isEqualTo(user.getId());
        assertThat(event.getResourceId()).isEqualTo(String.valueOf(application.getId()));
        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getIpAddress()).isNotNull();
    }

    @Test
    void deletingDashboardCreatesAuditEvent() throws Exception {
        User user = createUser("grace");
        Dashboard dashboard = createDashboard(user.getId());

        mockMvc.perform(delete("/api/v1/dashboards/{id}", dashboard.getId())
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isNoContent());

        List<AuditEvent> events = auditEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAction()).isEqualTo("DASHBOARD_DELETED");
        assertThat(events.get(0).getUserId()).isEqualTo(user.getId());
        assertThat(events.get(0).getResourceId()).isEqualTo(String.valueOf(dashboard.getId()));
    }

    @Test
    void auditEventsAreReturnedNewestFirstWithResolvedUsername() throws Exception {
        User user = createUser("linus");
        Application application = createApplication(user.getId());
        Dashboard dashboard = createDashboard(user.getId());

        mockMvc.perform(post("/api/applications/{id}/regenerate-api-key", application.getId())
                .header("Authorization", bearer(user.getId()))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/dashboards/{id}", dashboard.getId())
                .header("Authorization", bearer(user.getId()))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/audit-events")
                        .param("page", "0")
                        .param("size", "50")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].action").value("DASHBOARD_DELETED"))
                .andExpect(jsonPath("$.content[1].action").value("API_KEY_REGENERATED"))
                .andExpect(jsonPath("$.content[0].username").value("linus"))
                .andExpect(jsonPath("$.content[0].userId").value(user.getId()));
    }

    @Test
    void auditEventsAreScopedToTheAuthenticatedUser() throws Exception {
        User owner = createUser("owner");
        User other = createUser("other");
        Application application = createApplication(owner.getId());

        mockMvc.perform(post("/api/applications/{id}/regenerate-api-key", application.getId())
                .header("Authorization", bearer(owner.getId()))).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-events").header("Authorization", bearer(other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/audit-events").header("Authorization", bearer(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void actionFilterReturnsOnlyMatchingEvents() throws Exception {
        User user = createUser("filter");
        Application application = createApplication(user.getId());
        Dashboard dashboard = createDashboard(user.getId());

        mockMvc.perform(post("/api/applications/{id}/regenerate-api-key", application.getId())
                .header("Authorization", bearer(user.getId()))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/dashboards/{id}", dashboard.getId())
                .header("Authorization", bearer(user.getId()))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/audit-events")
                        .param("action", "DASHBOARD_DELETED")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("DASHBOARD_DELETED"));
    }

    @Test
    void xForwardedForIsCaptured() throws Exception {
        User user = createUser("proxy");
        Application application = createApplication(user.getId());

        mockMvc.perform(post("/api/applications/{id}/regenerate-api-key", application.getId())
                        .header("Authorization", bearer(user.getId()))
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isOk());

        assertThat(auditEventRepository.findAll().get(0).getIpAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    void deletingAuditEventsIsNotAllowed() throws Exception {
        User user = createUser("delete");
        createAuditEvent(user.getId(), "API_KEY_REGENERATED", "1");

        Long eventId = auditEventRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/api/v1/audit-events/{id}", eventId)
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/v1/audit-events").header("Authorization", bearer(user.getId())))
                .andExpect(status().isMethodNotAllowed());

        assertThat(auditEventRepository.count()).isEqualTo(1);
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPageSizeIsRejected() throws Exception {
        User user = createUser("validation");

        mockMvc.perform(get("/api/v1/audit-events")
                        .param("size", "1000")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isBadRequest());
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("encoded-secret")
                .emailVerified(true)
                .role("USER")
                .build());
    }

    private Application createApplication(Long ownerId) {
        return applicationRepository.save(Application.builder()
                .name("app-" + ownerId)
                .apiKeyHash("app_hash_" + ownerId + "_" + System.nanoTime())
                .ownerId(ownerId)
                .build());
    }

    private Dashboard createDashboard(Long ownerId) {
        return dashboardRepository.save(Dashboard.builder()
                .name("dashboard-" + ownerId)
                .ownerId(ownerId)
                .build());
    }

    private void createAuditEvent(Long userId, String action, String resourceId) {
        // Written directly through the repository (append-only insert) to seed a row without
        // depending on an endpoint; the aspect uses the same repository under the hood.
        auditEventRepository.save(AuditEvent.builder()
                .userId(userId)
                .action(action)
                .resourceId(resourceId)
                .ipAddress("127.0.0.1")
                .timestamp(Instant.now())
                .build());
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "user-" + userId);
    }
}
