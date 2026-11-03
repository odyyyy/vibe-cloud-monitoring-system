package com.monitoring.api;

import com.monitoring.auth.entity.Role;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.auth.service.EmailSender;
import com.monitoring.collector.repository.LogPipelineRuleRepository;
import com.monitoring.organization.entity.Membership;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.entity.Organization;
import com.monitoring.organization.repository.MembershipRepository;
import com.monitoring.organization.repository.OrganizationRepository;
import com.monitoring.organization.tenant.CurrentTenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for the JWT security matcher of the log pipeline API: the endpoint must be
 * reachable for authenticated members, while writes stay restricted to ADMIN/OWNER.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:log-pipelines-security-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class LogPipelinesSecurityIntegrationTest {

    private static final String CREATE_BODY = "{\"name\":\"Drop debug\",\"applicationId\":null,"
            + "\"filter\":\"level:DEBUG\",\"action\":\"DROP\",\"actionConfig\":{}}";

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
    private LogPipelineRuleRepository pipelineRuleRepository;

    @MockBean
    private EmailSender emailSender;

    @Test
    void ownerCanListAndCreateRules() throws Exception {
        Account owner = createAccount("pipelines-owner", MembershipRole.OWNER);

        mockMvc.perform(authorized(get("/api/v1/log-pipelines"), owner))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(post("/api/v1/log-pipelines"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());

        assertEquals(1, pipelineRuleRepository
                .findByOrganizationIdOrderByOrderIndexAscIdAsc(owner.organizationId()).size());
    }

    @Test
    void viewerCanReadButCannotCreateRules() throws Exception {
        Account viewer = createAccount("pipelines-viewer", MembershipRole.VIEWER);

        mockMvc.perform(authorized(get("/api/v1/log-pipelines"), viewer))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(post("/api/v1/log-pipelines"), viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/log-pipelines"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request,
                                                     Account account) {
        return request
                .header(HttpHeaders.AUTHORIZATION, bearer(account.user()))
                .header(CurrentTenantService.ORGANIZATION_HEADER, account.organizationId());
    }

    private Account createAccount(String username, MembershipRole role) {
        User user = userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("encoded-secret")
                .emailVerified(true)
                .role(Role.USER)
                .build());
        Organization organization = organizationRepository.save(
                Organization.builder().name(username + " org").build());
        membershipRepository.save(Membership.builder()
                .organizationId(organization.getId())
                .userId(user.getId())
                .role(role)
                .build());
        return new Account(user, organization.getId());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateToken(user.getId(), "user-" + user.getId());
    }

    private record Account(User user, Long organizationId) {
    }
}
