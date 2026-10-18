package com.monitoring.api;

import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.auth.organization.entity.Membership;
import com.monitoring.auth.organization.entity.MembershipRole;
import com.monitoring.auth.organization.entity.Organization;
import com.monitoring.auth.organization.repository.MembershipRepository;
import com.monitoring.auth.organization.repository.OrganizationRepository;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level contract for {@code GET /api/v1/overview}: the endpoint is reachable with a valid
 * bearer token (never 403) and remains closed to anonymous callers.
 *
 * <p>This guards the security configuration as well as the controller: an endpoint that exists
 * but is missing from {@code SecurityConfig} falls through to {@code anyRequest().denyAll()},
 * which returns 403 for authenticated users instead of the summary.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:overview-http-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class OverviewControllerIntegrationTest {

    private static final String OWNER_USERNAME = "overview-owner";
    private static final String OTHER_USERNAME = "overview-other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MetricSampleRepository metricSampleRepository;

    @Autowired
    private LogEventRepository logEventRepository;

    private User owner;
    private User other;
    private Long ownerOrganizationId;
    private Long otherOrganizationId;

    @BeforeEach
    void setUp() {
        logEventRepository.deleteAll();
        metricSampleRepository.deleteAll();
        applicationRepository.deleteAll();
        membershipRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();

        owner = createUser(OWNER_USERNAME);
        other = createUser(OTHER_USERNAME);
        ownerOrganizationId = createOrganization(owner);
        otherOrganizationId = createOrganization(other);
    }

    @Test
    void anonymousRequest_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequest_returnsWorkspaceCounters() throws Exception {
        Application application = applicationRepository.save(Application.builder()
                .name("overview-http-app")
                .apiKeyHash("app_overview_http")
                .organizationId(ownerOrganizationId)
                .build());

        Instant recent = Instant.now().minusSeconds(60);
        metricSampleRepository.save(MetricSample.builder()
                .application(application)
                .name("system.cpu.usage")
                .value(1.0)
                .timestamp(recent)
                .receivedAt(recent)
                .tags(new HashMap<>())
                .build());
        logEventRepository.save(LogEvent.builder()
                .application(application)
                .level(LogLevel.INFO)
                .message("event")
                .timestamp(recent)
                .receivedAt(recent)
                .build());

        mockMvc.perform(get("/api/v1/overview").header("Authorization", bearer(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationCount").value(1))
                .andExpect(jsonPath("$.metricNameCount24h").value(1))
                .andExpect(jsonPath("$.logEventCount24h").value(1));
    }

    @Test
    void countersAreScopedToTheAuthenticatedUser() throws Exception {
        applicationRepository.save(Application.builder()
                .name("overview-http-other")
                .apiKeyHash("app_overview_http_other")
                .organizationId(otherOrganizationId)
                .build());

        mockMvc.perform(get("/api/v1/overview").header("Authorization", bearer(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationCount").value(0))
                .andExpect(jsonPath("$.metricNameCount24h").value(0))
                .andExpect(jsonPath("$.logEventCount24h").value(0));
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

    private Long createOrganization(User user) {
        Organization organization = organizationRepository.save(
                Organization.builder().name(user.getUsername() + " org").build());
        membershipRepository.save(Membership.builder()
                .organizationId(organization.getId())
                .userId(user.getId())
                .role(MembershipRole.OWNER)
                .build());
        return organization.getId();
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "user-" + userId);
    }
}
