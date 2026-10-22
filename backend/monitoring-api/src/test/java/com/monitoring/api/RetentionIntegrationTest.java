package com.monitoring.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.audit.repository.AuditEventRepository;
import com.monitoring.auth.entity.Role;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.auth.service.EmailSender;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.MetricAggregate;
import com.monitoring.collector.entity.MetricGranularity;
import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.entity.SpanStatus;
import com.monitoring.collector.entity.TraceSpan;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricAggregateRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.collector.repository.TraceSpanRepository;
import com.monitoring.organization.dto.response.OrganizationResponse;
import com.monitoring.organization.repository.MembershipRepository;
import com.monitoring.organization.repository.OrganizationRepository;
import com.monitoring.organization.service.OrganizationService;
import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.retention.entity.RetentionPolicy;
import com.monitoring.retention.repository.RetentionPolicyRepository;
import com.monitoring.retention.repository.RetentionPurgeRunRepository;
import com.monitoring.retention.service.RetentionPurgeJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of per-organization retention: defaults, role enforcement, audit
 * recording and the tenant-scoped purge against the real Flyway schema.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:retention-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // Retention is disabled in test resources; this class invokes the purge explicitly and
        // must not have a live cron racing its assertions.
        "monitoring.retention.enabled=true",
        "monitoring.retention.cron=-"
})
@AutoConfigureMockMvc
class RetentionIntegrationTest {

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
    private LogEventRepository logEventRepository;

    @Autowired
    private MetricSampleRepository metricSampleRepository;

    @Autowired
    private MetricAggregateRepository metricAggregateRepository;

    @Autowired
    private TraceSpanRepository traceSpanRepository;

    @Autowired
    private RetentionPolicyRepository retentionPolicyRepository;

    @Autowired
    private RetentionPurgeRunRepository retentionPurgeRunRepository;

    @Autowired
    private RetentionPurgeJob retentionPurgeJob;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @MockBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        retentionPurgeRunRepository.deleteAll();
        retentionPolicyRepository.deleteAll();
        traceSpanRepository.deleteAll();
        metricAggregateRepository.deleteAll();
        metricSampleRepository.deleteAll();
        logEventRepository.deleteAll();
        auditEventRepository.deleteAll();
        applicationRepository.deleteAll();
        membershipRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getReturnsEffectiveDefaultsAndLimits() throws Exception {
        Account owner = createAccount("owner");

        mockMvc.perform(get("/api/v1/organizations/current/retention")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logsRetentionDays").value(14))
                .andExpect(jsonPath("$.metricsRetentionDays").value(30))
                .andExpect(jsonPath("$.tracesRetentionDays").value(7))
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.maxLogsRetentionDays").value(90))
                .andExpect(jsonPath("$.maxMetricsRetentionDays").value(365))
                .andExpect(jsonPath("$.maxTracesRetentionDays").value(30));
    }

    @Test
    void adminUpdatesThePolicyAndTheChangeIsAudited() throws Exception {
        Account owner = createAccount("owner");
        Account admin = createAccount("admin");
        invite(owner, admin);
        promote(owner, admin, "ADMIN");

        mockMvc.perform(put("/api/v1/organizations/current/retention")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "logsRetentionDays", 30,
                                "metricsRetentionDays", 60,
                                "tracesRetentionDays", 14))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logsRetentionDays").value(30))
                .andExpect(jsonPath("$.metricsRetentionDays").value(60))
                .andExpect(jsonPath("$.tracesRetentionDays").value(14))
                .andExpect(jsonPath("$.updatedBy").value(admin.user().getId()));

        mockMvc.perform(get("/api/v1/organizations/current/retention")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logsRetentionDays").value(30));

        assertThat(auditEventRepository.findAll())
                .anySatisfy(event -> {
                    assertThat(event.getAction()).isEqualTo("RETENTION_POLICY_UPDATED");
                    assertThat(event.getOrganizationId()).isEqualTo(owner.organizationId());
                    assertThat(event.getUserId()).isEqualTo(admin.user().getId());
                });
    }

    @Test
    void viewerCannotChangeThePolicyAndValuesAboveTheLimitAreRejected() throws Exception {
        Account owner = createAccount("owner");
        Account viewer = createAccount("viewer");
        invite(owner, viewer);

        mockMvc.perform(put("/api/v1/organizations/current/retention")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "logsRetentionDays", 30,
                                "metricsRetentionDays", 60,
                                "tracesRetentionDays", 14))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/organizations/current/retention")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, owner.organizationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "logsRetentionDays", 30,
                                "metricsRetentionDays", 999,
                                "tracesRetentionDays", 14))))
                .andExpect(status().isBadRequest());

        assertThat(retentionPolicyRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void purgeDeletesOnlyExpiredRowsOfTheOrganizationOwningThem() throws Exception {
        Account first = createAccount("first");
        Account second = createAccount("second");
        Application firstApp = application(first, "first-app");
        Application secondApp = application(second, "second-app");

        Instant now = Instant.now();
        LogEvent firstOld = logEvent(firstApp, now.minus(20, ChronoUnit.DAYS));
        LogEvent firstRecent = logEvent(firstApp, now.minus(1, ChronoUnit.DAYS));
        LogEvent secondOld = logEvent(secondApp, now.minus(20, ChronoUnit.DAYS));
        MetricSample firstOldSample = metricSample(firstApp, now.minus(45, ChronoUnit.DAYS));
        MetricSample firstRecentSample = metricSample(firstApp, now.minus(1, ChronoUnit.DAYS));
        MetricAggregate firstOldAggregate = metricAggregate(firstApp, now.minus(45, ChronoUnit.DAYS));
        MetricAggregate firstRecentAggregate = metricAggregate(firstApp, now.minus(1, ChronoUnit.DAYS));
        MetricAggregate secondOldAggregate = metricAggregate(secondApp, now.minus(45, ChronoUnit.DAYS));
        TraceSpan firstOldSpan = traceSpan(firstApp, now.minus(10, ChronoUnit.DAYS));
        TraceSpan firstRecentSpan = traceSpan(firstApp, now.minus(1, ChronoUnit.DAYS));

        // The second organization keeps 90 days of logs, so its 20-day-old row must survive.
        retentionPolicyRepository.save(RetentionPolicy.builder()
                .organizationId(second.organizationId())
                .logsRetentionDays(90)
                .metricsRetentionDays(365)
                .tracesRetentionDays(30)
                .updatedAt(now)
                .build());

        retentionPurgeJob.purgeExpiredData();

        assertThat(logEventRepository.existsById(firstOld.getId())).isFalse();
        assertThat(logEventRepository.existsById(firstRecent.getId())).isTrue();
        assertThat(logEventRepository.existsById(secondOld.getId())).isTrue();
        assertThat(metricSampleRepository.existsById(firstOldSample.getId())).isFalse();
        assertThat(metricSampleRepository.existsById(firstRecentSample.getId())).isTrue();
        assertThat(metricAggregateRepository.existsById(firstOldAggregate.getId())).isFalse();
        assertThat(metricAggregateRepository.existsById(firstRecentAggregate.getId())).isTrue();
        assertThat(metricAggregateRepository.existsById(secondOldAggregate.getId())).isTrue();
        assertThat(traceSpanRepository.existsById(firstOldSpan.getId())).isFalse();
        assertThat(traceSpanRepository.existsById(firstRecentSpan.getId())).isTrue();

        assertThat(retentionPurgeRunRepository.findRecent(first.organizationId(),
                PageRequest.of(0, 10))).hasSize(3);
        assertThat(retentionPurgeRunRepository.findRecent(second.organizationId(),
                PageRequest.of(0, 10))).hasSize(3);
    }

    @Test
    void purgeHistoryIsScopedToTheActiveOrganization() throws Exception {
        Account first = createAccount("first");
        Account second = createAccount("second");
        retentionPolicyRepository.save(RetentionPolicy.builder()
                .organizationId(first.organizationId())
                .logsRetentionDays(14)
                .metricsRetentionDays(30)
                .tracesRetentionDays(7)
                .updatedAt(Instant.now())
                .build());

        retentionPurgeJob.purgeExpiredData();

        mockMvc.perform(get("/api/v1/organizations/current/retention/runs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, first.organizationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/api/v1/organizations/current/retention/runs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(second.user().getId()))
                        .header(CurrentTenantService.ORGANIZATION_HEADER, second.organizationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        assertThat(retentionPurgeRunRepository.findRecent(first.organizationId(),
                PageRequest.of(0, 10))).hasSize(3);
        assertThat(retentionPurgeRunRepository.findRecent(second.organizationId(),
                PageRequest.of(0, 10))).hasSize(3);
    }

    private void invite(Account owner, Account invited) throws Exception {
        mockMvc.perform(post("/api/v1/organizations/{id}/members", owner.organizationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", invited.user().getEmail()))))
                .andExpect(status().isCreated());
    }

    private void promote(Account owner, Account member, String role) throws Exception {
        mockMvc.perform(patch("/api/v1/organizations/{org}/members/{user}",
                                owner.organizationId(), member.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.user().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", role))))
                .andExpect(status().isOk());
    }

    private Account createAccount(String username) {
        User user = userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("encoded-secret")
                .emailVerified(true)
                .role(Role.USER)
                .build());
        OrganizationResponse organization = organizationService.createPersonal(user.getId(), username);
        return new Account(user, organization.id());
    }

    private Application application(Account account, String name) {
        return applicationRepository.save(Application.builder()
                .name(name)
                .apiKeyHash(name + "-hash")
                .organizationId(account.organizationId())
                .build());
    }

    private LogEvent logEvent(Application application, Instant timestamp) {
        return logEventRepository.save(LogEvent.builder()
                .application(application)
                .level(LogLevel.INFO)
                .message("event")
                .timestamp(timestamp)
                .receivedAt(Instant.now())
                .build());
    }

    private MetricSample metricSample(Application application, Instant timestamp) {
        return metricSampleRepository.save(MetricSample.builder()
                .application(application)
                .name("cpu_usage")
                .value(1.0)
                .timestamp(timestamp)
                .receivedAt(Instant.now())
                .tags(Map.of("host", "s1"))
                .build());
    }

    private MetricAggregate metricAggregate(Application application, Instant bucketStart) {
        return metricAggregateRepository.save(MetricAggregate.builder()
                .application(application)
                .name("cpu_usage")
                .granularity(MetricGranularity.HOURLY)
                .bucketEpoch(bucketStart.getEpochSecond())
                .sampleCount(2)
                .valueAvg(1.0)
                .valueMax(1.5)
                .valueMin(0.5)
                .build());
    }

    private TraceSpan traceSpan(Application application, Instant startTime) {
        return traceSpanRepository.save(TraceSpan.builder()
                .traceId("trace-" + startTime.toEpochMilli())
                .spanId("span-" + startTime.toEpochMilli())
                .application(application)
                .serviceName("api")
                .operationName("GET /health")
                .startTime(startTime)
                .endTime(startTime.plusMillis(5))
                .durationMs(5)
                .status(SpanStatus.OK)
                .build());
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
