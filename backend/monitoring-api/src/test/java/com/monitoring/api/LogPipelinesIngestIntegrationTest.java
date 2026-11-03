package com.monitoring.api;

import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.apps.security.ApiKeyHasher;
import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.collector.dto.response.LogFacetsResponse;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogPipelineAction;
import com.monitoring.collector.entity.LogPipelineRule;
import com.monitoring.collector.facet.LogFacetField;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.LogPipelineRuleRepository;
import com.monitoring.collector.service.LogFacetService;
import com.monitoring.organization.entity.Organization;
import com.monitoring.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification of the backend pipeline fallback: rules are evaluated before saving, so
 * a dropped event costs no storage and fields extracted by PARSE_REGEX become available to the
 * log explorer's dynamic facets.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:log-pipelines-ingest-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.logs.facets.max-requests=1000"
})
@AutoConfigureMockMvc
class LogPipelinesIngestIntegrationTest {

    private static final String API_KEY = "app_log_pipelines_it";
    private static final Instant FROM = Instant.parse("2026-10-09T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-09T23:59:59Z");

    private static final String TWO_EVENTS = "{\"events\":["
            + "{\"level\":\"DEBUG\",\"message\":\"cache miss\","
            + "\"timestamp\":\"2026-10-09T10:00:00Z\",\"context\":\"{\\\"service\\\":\\\"checkout\\\"}\"},"
            + "{\"level\":\"ERROR\",\"message\":\"order 12345 failed\","
            + "\"timestamp\":\"2026-10-09T10:01:00Z\",\"context\":\"{\\\"service\\\":\\\"checkout\\\"}\"}"
            + "]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private LogPipelineRuleRepository pipelineRuleRepository;

    @Autowired
    private LogFacetService logFacetService;

    private Organization organization;
    private Application application;

    @BeforeEach
    void setUp() {
        logEventRepository.deleteAll();
        pipelineRuleRepository.deleteAll();
        applicationRepository.deleteAll();
        organization = organizationRepository.save(
                Organization.builder().name("log-pipelines-it").build());
        application = applicationRepository.save(Application.builder()
                .name("log-pipelines-it")
                .apiKeyHash(ApiKeyHasher.hash(API_KEY))
                .organizationId(organization.getId())
                .build());
    }

    @Test
    void dropsFilteredEventsAndExposesParsedFieldsToFacets() throws Exception {
        saveRule(organization, null, "Drop debug", "level:DEBUG",
                LogPipelineAction.DROP, "{}", 0);
        saveRule(organization, null, "Extract order id", "level:ERROR",
                LogPipelineAction.PARSE_REGEX,
                "{\"pattern\":\"order (?P<order_id>\\\\d+)\"}", 1);

        ingest(TWO_EVENTS, API_KEY);

        assertEquals(1, logEventRepository.count());
        LogEvent stored = logEventRepository.findAll().get(0);
        assertEquals("order 12345 failed", stored.getMessage());
        assertTrue(stored.getContext().contains("\"order_id\":\"12345\""));

        LogFacetsResponse facets = logFacetService.facets(
                organization.getId(),
                query(),
                List.of(LogFacetField.fromName("context.order_id").orElseThrow()),
                5);
        assertEquals("12345", facets.facets().get("context.order_id").get(0).value());
    }

    @Test
    void doesNotApplyRulesOwnedByAnotherOrganization() throws Exception {
        Organization other = organizationRepository.save(
                Organization.builder().name("log-pipelines-other").build());
        saveRule(other, null, "Foreign drop", "level:DEBUG",
                LogPipelineAction.DROP, "{}", 0);

        ingest(TWO_EVENTS, API_KEY);

        assertEquals(2, logEventRepository.count());
    }

    @Test
    void appliesApplicationScopedRulesOnlyToThatApplication() throws Exception {
        Application other = applicationRepository.save(Application.builder()
                .name("log-pipelines-other")
                .apiKeyHash(ApiKeyHasher.hash("app_log_pipelines_other"))
                .organizationId(organization.getId())
                .build());
        saveRule(organization, other, "Drop debug for other", "level:DEBUG",
                LogPipelineAction.DROP, "{}", 0);

        ingest(TWO_EVENTS, API_KEY);

        assertEquals(2, logEventRepository.count());
    }

    private void saveRule(Organization owner,
                          Application scope,
                          String name,
                          String filter,
                          LogPipelineAction action,
                          String actionConfig,
                          int orderIndex) {
        pipelineRuleRepository.save(LogPipelineRule.builder()
                .organizationId(owner.getId())
                .application(scope)
                .name(name)
                .filterExpression(filter)
                .action(action)
                .actionConfig(actionConfig)
                .orderIndex(orderIndex)
                .build());
    }

    private void ingest(String body, String apiKey) throws Exception {
        mockMvc.perform(post("/api/v1/ingest/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }

    private LogsQueryRequest query() {
        return LogsQueryRequest.builder()
                .applicationId(application.getId())
                .from(FROM)
                .to(TO)
                .page(0)
                .size(25)
                .build();
    }
}
