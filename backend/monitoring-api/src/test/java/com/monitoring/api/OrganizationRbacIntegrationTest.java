package com.monitoring.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.auth.organization.dto.OrganizationResponse;
import com.monitoring.auth.organization.repository.MembershipRepository;
import com.monitoring.auth.organization.repository.OrganizationRepository;
import com.monitoring.auth.organization.service.OrganizationService;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.auth.service.EmailSender;
import com.monitoring.storage.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of organizations, the active-organization header and role enforcement.
 *
 * <p>These tests exercise the real security filter chain, tenant resolution, the
 * {@code @RequireRole} aspect and the Flyway schema, so a regression in any layer that would
 * let one tenant read or mutate another tenant's resources fails here.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:organization-rbac-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class OrganizationRbacIntegrationTest {

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

    @MockBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        membershipRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registrationCreatesPersonalOrganizationOwnedByTheUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "newbie",
                                "email", "newbie@example.com",
                                "password", "supersecret"))))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("newbie").orElseThrow();

        mockMvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("newbie organization"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void creatingOrganizationMakesTheCallerTheOwner() throws Exception {
        Account owner = createAccount("creator");

        mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Team")))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Team"))
                .andExpect(jsonPath("$.role").value("OWNER"));

        mockMvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void viewerCanReadButCannotMutateOrganizationResources() throws Exception {
        Account owner = createAccount("owner");
        Account viewer = createAccount("viewer");
        invite(owner, viewer, "VIEWER");

        mockMvc.perform(get("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "forbidden-app"))))
                .andExpect(status().isForbidden());

        assertThat(applicationRepository.count()).isZero();
    }

    @Test
    void adminCanMutateOrganizationResources() throws Exception {
        Account owner = createAccount("owner");
        Account admin = createAccount("admin");
        invite(owner, admin, "ADMIN");

        mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "admin-app"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("admin-app"));
    }

    @Test
    void crossTenantAccessIsRejected() throws Exception {
        Account first = createAccount("first");
        Account second = createAccount("second");

        mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, second.organizationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "stolen-app"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/organizations/{id}/members", second.organizationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.user().getId())))
                .andExpect(status().isForbidden());

        assertThat(applicationRepository.count()).isZero();
    }

    @Test
    void applicationsAreScopedToTheActiveOrganization() throws Exception {
        Account first = createAccount("first");
        Account second = createAccount("second");

        mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, first.organizationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "first-app"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/applications").header(HttpHeaders.AUTHORIZATION, bearer(second.user().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/applications").header(HttpHeaders.AUTHORIZATION, bearer(first.user().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("first-app"));
    }

    @Test
    void onlyOwnersCanDeleteAnOrganizationAndTheLastOwnerIsProtected() throws Exception {
        Account owner = createAccount("owner");
        Account admin = createAccount("admin");
        invite(owner, admin, "ADMIN");

        mockMvc.perform(patch("/api/v1/organizations/{org}/members/{user}", owner.organizationId(), owner.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ADMIN"))))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/organizations/{id}", owner.organizationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.user().getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/organizations/{id}", owner.organizationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId())))
                .andExpect(status().isNoContent());

        assertThat(organizationRepository.findById(owner.organizationId())).isEmpty();
        assertThat(membershipRepository.findByOrganizationIdAndUserId(owner.organizationId(),
                owner.user().getId())).isEmpty();
    }

    private void invite(Account owner, Account invited, String role) throws Exception {
        mockMvc.perform(post("/api/v1/organizations/{id}/members", owner.organizationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", invited.user().getEmail()))))
                .andExpect(status().isCreated());

        if (!"VIEWER".equals(role)) {
            mockMvc.perform(patch("/api/v1/organizations/{org}/members/{user}",
                            owner.organizationId(), invited.user().getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("role", role))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(role));
        }
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

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private record Account(User user, Long organizationId) {
    }
}
