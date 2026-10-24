package com.monitoring.api;

import com.monitoring.alerts.dto.request.AddIncidentCommentRequest;
import com.monitoring.alerts.dto.response.IncidentCommentResponse;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.alerts.exception.AlertIncidentAccessDeniedException;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.alerts.repository.IncidentCommentRepository;
import com.monitoring.alerts.service.IncidentCommentService;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.organization.entity.Membership;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.entity.Organization;
import com.monitoring.organization.exception.TenantAccessDeniedException;
import com.monitoring.organization.repository.MembershipRepository;
import com.monitoring.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incident-comments-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.healthcheck.scheduler.enabled=false",
        "monitoring.alerts.scheduler.enabled=false"
})
class IncidentCommentsIntegrationTest {

    @Autowired
    private IncidentCommentService commentService;

    @Autowired
    private IncidentCommentRepository commentRepository;

    @Autowired
    private AlertIncidentRepository incidentRepository;

    @Autowired
    private AlertRuleRepository ruleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Organization organization;
    private User author;
    private AlertIncident incident;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        incidentRepository.deleteAll();
        ruleRepository.deleteAll();
        membershipRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();

        organization = organizationRepository.save(
                Organization.builder().name("Acme").build());
        author = createUser("alice");
        addMembership(organization.getId(), author.getId(), MembershipRole.ADMIN);
        incident = createIncident(organization.getId());
    }

    @Test
    void storesCommentsAndReturnsAuthorRoleNewestFirst() {
        commentService.addComment(organization.getId(), author.getId(), MembershipRole.ADMIN,
                incident.getId(), request("Investigating now, DB CPU is high."));
        commentService.addComment(organization.getId(), author.getId(), MembershipRole.ADMIN,
                incident.getId(), request("Second update"));

        PageResponse<IncidentCommentResponse> page =
                commentService.listComments(organization.getId(), incident.getId(), 0, 10);

        assertEquals(2, page.totalElements());
        assertEquals("Second update", page.content().get(0).getText());
        assertEquals("Investigating now, DB CPU is high.", page.content().get(1).getText());
        assertEquals("alice", page.content().get(0).getUsername());
        assertEquals(MembershipRole.ADMIN, page.content().get(0).getRole());
        assertFalse(page.content().get(0).isSystem());
    }

    @Test
    void slashCommandChangesIncidentStateAndAddsSystemComment() {
        IncidentCommentResponse posted = commentService.addComment(
                organization.getId(), author.getId(), MembershipRole.ADMIN,
                incident.getId(), request("/acknowledge on it"));

        assertFalse(posted.isSystem());

        AlertIncident updated = incidentRepository.findById(incident.getId()).orElseThrow();
        assertEquals(IncidentStatus.ACKNOWLEDGED, updated.getStatus());
        assertEquals(author.getId(), updated.getAcknowledgedBy());

        PageResponse<IncidentCommentResponse> page =
                commentService.listComments(organization.getId(), incident.getId(), 0, 10);
        assertEquals(2, page.totalElements());
        assertTrue(page.content().get(0).isSystem());
        assertEquals("alice acknowledged this incident.", page.content().get(0).getText());
    }

    @Test
    void commentsAreTenantScoped() {
        Organization other = organizationRepository.save(Organization.builder().name("Other").build());

        commentService.addComment(organization.getId(), author.getId(), MembershipRole.ADMIN,
                incident.getId(), request("Private context"));

        assertThrows(AlertIncidentAccessDeniedException.class,
                () -> commentService.listComments(other.getId(), incident.getId(), 0, 10));
        assertThrows(AlertIncidentAccessDeniedException.class,
                () -> commentService.addComment(other.getId(), author.getId(), MembershipRole.ADMIN,
                        incident.getId(), request("Intrusion")));
    }

    @Test
    void viewersCannotChangeIncidentStateFromAComment() {
        User viewer = createUser("bob");
        addMembership(organization.getId(), viewer.getId(), MembershipRole.VIEWER);

        assertThrows(TenantAccessDeniedException.class,
                () -> commentService.addComment(organization.getId(), viewer.getId(),
                        MembershipRole.VIEWER, incident.getId(), request("/resolve")));

        assertEquals(0, commentRepository.count());
        assertEquals(IncidentStatus.FIRING,
                incidentRepository.findById(incident.getId()).orElseThrow().getStatus());
    }

    @Test
    void commentMarkupIsRemovedBeforeStorage() {
        commentService.addComment(organization.getId(), author.getId(), MembershipRole.ADMIN,
                incident.getId(),
                request("<script>alert(1)</script>**Investigating** dashboard:456"));

        IncidentCommentResponse stored = commentService
                .listComments(organization.getId(), incident.getId(), 0, 10)
                .content()
                .get(0);

        assertEquals("**Investigating** dashboard:456", stored.getText());
    }

    private AddIncidentCommentRequest request(String text) {
        return AddIncidentCommentRequest.builder().text(text).build();
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("encoded-secret")
                .emailVerified(true)
                .build());
    }

    private void addMembership(Long organizationId, Long userId, MembershipRole role) {
        membershipRepository.save(Membership.builder()
                .organizationId(organizationId)
                .userId(userId)
                .role(role)
                .build());
    }

    private AlertIncident createIncident(Long organizationId) {
        AlertRule rule = ruleRepository.save(AlertRule.builder()
                .organizationId(organizationId)
                .name("High CPU")
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target("system.cpu.usage")
                .condition(AlertCondition.GREATER_THAN)
                .threshold(90.0)
                .logicOperator(LogicOperator.AND)
                .durationMinutes(1)
                .active(true)
                .build());
        return incidentRepository.save(AlertIncident.builder()
                .rule(rule)
                .organizationId(organizationId)
                .status(IncidentStatus.FIRING)
                .triggeredAt(Instant.now())
                .build());
    }
}

