package com.monitoring.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.auth.entity.PersonalAccessToken;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.auth.repository.PersonalAccessTokenRepository;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.auth.service.EmailSender;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.dashboards.repository.WidgetRepository;
import com.monitoring.organization.dto.response.OrganizationResponse;
import com.monitoring.organization.repository.MembershipRepository;
import com.monitoring.organization.repository.OrganizationRepository;
import com.monitoring.organization.service.OrganizationService;
import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.apps.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of Personal Access Tokens: issuance, scope enforcement, revocation,
 * organization binding and the fact that a PAT can never exceed the owner's membership role.
 *
 * <p>Exercises the real filter chain ({@code PatAuthFilter} + {@code JwtAuthFilter}), tenant
 * resolution and the {@code @RequireRole} aspect against the Flyway schema.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal-access-token-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class PersonalAccessTokenIntegrationTest {

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
    private WidgetRepository widgetRepository;

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @MockBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        widgetRepository.deleteAll();
        dashboardRepository.deleteAll();
        personalAccessTokenRepository.deleteAll();
        applicationRepository.deleteAll();
        membershipRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void patIsStoredHashedAndOnlyGrantsItsIssuedScopes() throws Exception {
        Account owner = createAccount("owner");
        long applicationId = createApplication(owner);

        CreatedPat readOnly = createPat(owner, "reads", 30, List.of("read:metrics", "read:dashboards"));

        assertThat(personalAccessTokenRepository.findAll())
                .singleElement()
                .extracting(PersonalAccessToken::getTokenHash)
                .isNotEqualTo(readOnly.rawToken());

        mockMvc.perform(get("/api/v1/dashboards").header(HttpHeaders.AUTHORIZATION, raw(readOnly)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/metrics/names")
                        .param("applicationId", Long.toString(applicationId))
                        .header(HttpHeaders.AUTHORIZATION, raw(readOnly)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/dashboards")
                        .header(HttpHeaders.AUTHORIZATION, raw(readOnly))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "should-not-exist"))))
                .andExpect(status().isForbidden());

        assertThat(dashboardRepository.count()).isZero();
    }

    @Test
    void patWithWriteScopeCanCreateDashboards() throws Exception {
        Account owner = createAccount("owner");
        CreatedPat writer = createPat(owner, "writer", 30, List.of("write:dashboards"));

        mockMvc.perform(post("/api/v1/dashboards")
                        .header(HttpHeaders.AUTHORIZATION, raw(writer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "from-agent"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("from-agent"));
    }

    @Test
    void patIsBoundToItsOrganizationAndCannotBeReusedElsewhere() throws Exception {
        Account owner = createAccount("owner");
        Account other = createAccount("other");
        CreatedPat pat = createPat(owner, "bound", 30, List.of("read:dashboards"));

        mockMvc.perform(get("/api/v1/dashboards")
                        .header(HttpHeaders.AUTHORIZATION, raw(pat))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, other.organizationId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokedPatIsRejected() throws Exception {
        Account owner = createAccount("owner");
        CreatedPat pat = createPat(owner, "temporary", 30, List.of("read:dashboards"));

        mockMvc.perform(delete("/api/v1/settings/pats/{id}", pat.tokenId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboards").header(HttpHeaders.AUTHORIZATION, raw(pat)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredPatIsRejected() throws Exception {
        Account owner = createAccount("owner");
        String rawToken = "pat_expired_integration";
        personalAccessTokenRepository.save(PersonalAccessToken.builder()
                .userId(owner.user().getId())
                .organizationId(owner.organizationId())
                .name("expired")
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .scopes(Set.of("read:dashboards"))
                .build());

        mockMvc.perform(get("/api/v1/dashboards").header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patCannotManagePersonalTokensOrUnmappedRoutes() throws Exception {
        Account owner = createAccount("owner");
        CreatedPat pat = createPat(owner, "scoped", 30, List.of("read:dashboards"));

        mockMvc.perform(get("/api/v1/settings/pats").header(HttpHeaders.AUTHORIZATION, raw(pat)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, raw(pat)))
                .andExpect(status().isForbidden());
    }

    @Test
    void patCannotExceedTheOwnersMembershipRole() throws Exception {
        Account owner = createAccount("owner");
        Account viewer = createAccount("viewer");
        invite(owner, viewer);

        CreatedPat viewerPat = createPat(viewer, "viewer-write", 30, List.of("write:dashboards"),
                owner.organizationId());

        mockMvc.perform(post("/api/v1/dashboards")
                        .header(HttpHeaders.AUTHORIZATION, raw(viewerPat))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "viewer-attempt"))))
                .andExpect(status().isForbidden());

        assertThat(dashboardRepository.count()).isZero();
    }

    @Test
    void invalidScopeIsRejectedAtIssuance() throws Exception {
        Account owner = createAccount("owner");

        mockMvc.perform(post("/api/v1/settings/pats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "bad",
                                "expiresDays", 30,
                                "scopes", List.of("read:everything")))))
                .andExpect(status().isBadRequest());
    }

    private long createApplication(Account account) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(account.user().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "app-" + account.user().getId()))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private CreatedPat createPat(Account account, String name, int expiresDays, List<String> scopes) throws Exception {
        return createPat(account, name, expiresDays, scopes, null);
    }

    private CreatedPat createPat(Account account, String name, int expiresDays, List<String> scopes, Long organizationId)
            throws Exception {
        var request = post("/api/v1/settings/pats")
                .header(HttpHeaders.AUTHORIZATION, bearer(account.user().getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name, "expiresDays", expiresDays, "scopes", scopes)));
        if (organizationId != null) {
            request = request.header(CurrentTenantService.ORGANIZATION_HEADER, organizationId);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CreatedPat(body.get("rawToken").asText(), body.get("tokenId").asLong());
    }

    private void invite(Account owner, Account invited) throws Exception {
        mockMvc.perform(post("/api/v1/organizations/{id}/members", owner.organizationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", invited.user().getEmail()))))
                .andExpect(status().isCreated());
    }

    private Account createAccount(String username) {
        User user = userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("encoded-secret")
                .emailVerified(true)
                .role("USER")
                .build());
        OrganizationResponse organization = organizationService.createPersonal(user.getId(), username);
        return new Account(user, organization.id());
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "user-" + userId);
    }

    private String raw(CreatedPat pat) {
        return "Bearer " + pat.rawToken();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record Account(User user, Long organizationId) {
    }

    private record CreatedPat(String rawToken, Long tokenId) {
    }
}
