package com.monitoring.collector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.collector.dto.request.CreateLogPipelineRuleRequest;
import com.monitoring.collector.dto.request.LogEventPayload;
import com.monitoring.collector.dto.request.ReorderLogPipelineRulesRequest;
import com.monitoring.collector.dto.request.TestLogPipelineRequest;
import com.monitoring.collector.dto.request.UpdateLogPipelineRuleRequest;
import com.monitoring.collector.dto.response.LogPipelineRuleResponse;
import com.monitoring.collector.dto.response.LogPipelineTestResponse;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.LogPipelineAction;
import com.monitoring.collector.entity.LogPipelineRule;
import com.monitoring.collector.exception.InvalidLogPipelineRuleException;
import com.monitoring.collector.exception.LogPipelineRuleAccessDeniedException;
import com.monitoring.collector.exception.LogPipelineRuleLimitExceededException;
import com.monitoring.collector.exception.LogPipelineRuleNotFoundException;
import com.monitoring.collector.pipeline.LogPipelineEngine;
import com.monitoring.collector.repository.LogPipelineRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LogPipelineServiceTest {

    private static final Long ORG_ID = 42L;
    private static final Long OTHER_ORG_ID = 99L;
    private static final Long APP_ID = 7L;
    private static final Long RULE_ID = 5L;

    private LogPipelineRuleRepository repository;
    private ApplicationService applicationService;
    private LogPipelineService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(LogPipelineRuleRepository.class);
        applicationService = Mockito.mock(ApplicationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new LogPipelineService(
                repository, applicationService, new LogPipelineEngine(objectMapper), objectMapper);
        when(repository.save(any(LogPipelineRule.class))).thenAnswer(invocation -> {
            LogPipelineRule rule = invocation.getArgument(0);
            if (rule.getId() == null) {
                rule.setId(RULE_ID);
            }
            return rule;
        });
    }

    @Test
    void create_verifiesApplicationOwnershipAndAppendsToTheEnd() {
        when(applicationService.getOwnedApplication(APP_ID, ORG_ID)).thenReturn(application(APP_ID, ORG_ID));
        when(repository.findMaxOrderIndex(ORG_ID)).thenReturn(3);

        LogPipelineRuleResponse response = service.create(ORG_ID,
                createRequest("  drop debug  ", APP_ID, "level:DEBUG", LogPipelineAction.DROP, "{}"));

        verify(applicationService).getOwnedApplication(APP_ID, ORG_ID);
        assertEquals("drop debug", response.name());
        assertEquals(ORG_ID, response.organizationId());
        assertEquals(APP_ID, response.applicationId());
        assertEquals(4, response.orderIndex());
        assertEquals(LogPipelineAction.DROP, response.action());
    }

    @Test
    void create_withoutApplication_setsGlobalScope() {
        when(repository.findMaxOrderIndex(ORG_ID)).thenReturn(-1);

        LogPipelineRuleResponse response = service.create(ORG_ID,
                createRequest("global", null, "*", LogPipelineAction.DROP, "{}"));

        assertEquals(null, response.applicationId());
        assertEquals(0, response.orderIndex());
    }

    @Test
    void create_rejectsAForeignApplication() {
        when(applicationService.getOwnedApplication(APP_ID, ORG_ID))
                .thenThrow(new com.monitoring.apps.exception.ApplicationAccessDeniedException());

        assertThrows(InvalidLogPipelineRuleException.class,
                () -> service.create(ORG_ID, createRequest("bad", APP_ID, "*", LogPipelineAction.DROP, "{}")));

        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsTheFiftyFirstRule() {
        when(repository.countByOrganizationId(ORG_ID)).thenReturn(50L);

        assertThrows(LogPipelineRuleLimitExceededException.class,
                () -> service.create(ORG_ID, createRequest("too many", null, "*", LogPipelineAction.DROP, "{}")));

        verifyNoInteractions(applicationService);
        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsAForeignRule() {
        when(repository.findById(RULE_ID)).thenReturn(Optional.of(rule(OTHER_ORG_ID, null)));

        assertThrows(LogPipelineRuleAccessDeniedException.class,
                () -> service.update(ORG_ID, RULE_ID,
                        updateRequest("x", null, "*", LogPipelineAction.DROP, "{}")));

        verify(repository, never()).save(any());
    }

    @Test
    void update_throwsNotFoundForAnUnknownRule() {
        when(repository.findById(RULE_ID)).thenReturn(Optional.empty());

        assertThrows(LogPipelineRuleNotFoundException.class,
                () -> service.update(ORG_ID, RULE_ID,
                        updateRequest("x", null, "*", LogPipelineAction.DROP, "{}")));
    }

    @Test
    void update_storesTheCanonicalConfigAndReportedExtractedFields() {
        when(repository.findById(RULE_ID)).thenReturn(Optional.of(rule(ORG_ID, null)));

        LogPipelineRuleResponse response = service.update(ORG_ID, RULE_ID,
                updateRequest("parse", null, "*", LogPipelineAction.PARSE_REGEX,
                        "{\"pattern\":\"order (?P<order_id>\\\\d+)\"}"));

        assertEquals(List.of("order_id"), response.extractedFields());
        assertTrue(response.actionConfig().get("pattern").asText().contains("order_id"));
    }

    @Test
    void reorder_requiresEveryRuleExactlyOnce() {
        when(repository.findByOrganizationIdOrderByOrderIndexAscIdAsc(ORG_ID))
                .thenReturn(List.of(rule(ORG_ID, 1L), rule(ORG_ID, 2L)));

        assertThrows(InvalidLogPipelineRuleException.class,
                () -> service.reorder(ORG_ID, new ReorderLogPipelineRulesRequest(List.of(1L))));
        assertThrows(InvalidLogPipelineRuleException.class,
                () -> service.reorder(ORG_ID, new ReorderLogPipelineRulesRequest(List.of(1L, 1L))));
    }

    @Test
    void reorder_rewritesTheOrderIndexFromTheSubmittedIds() {
        LogPipelineRule first = rule(ORG_ID, 1L);
        LogPipelineRule second = rule(ORG_ID, 2L);
        when(repository.findByOrganizationIdOrderByOrderIndexAscIdAsc(ORG_ID))
                .thenReturn(List.of(first, second));
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<LogPipelineRuleResponse> ordered = service.reorder(
                ORG_ID, new ReorderLogPipelineRulesRequest(List.of(2L, 1L)));

        assertEquals(List.of(2L, 1L), ordered.stream().map(LogPipelineRuleResponse::id).toList());
        assertEquals(0, second.getOrderIndex());
        assertEquals(1, first.getOrderIndex());
    }

    @Test
    void agentRulesUseOnlyTheApplicationTenantAndId() {
        Application application = application(APP_ID, ORG_ID);
        when(repository.findApplicableRules(ORG_ID, APP_ID)).thenReturn(List.of());

        assertTrue(service.rulesForAgent(application).isEmpty());
        verify(repository).findApplicableRules(ORG_ID, APP_ID);
        verify(repository, never()).findApplicableRules(ORG_ID, OTHER_ORG_ID);
    }

    @Test
    void applyForIngestSkipsTheEngineWhenNoRulesExist() {
        Application application = application(APP_ID, ORG_ID);
        when(repository.findApplicableRules(ORG_ID, APP_ID)).thenReturn(List.of());

        LogPipelineEngine.PipelineResult result = service.applyForIngest(
                application, LogLevel.INFO, "started", "{}");

        assertFalse(result.dropped());
        assertEquals("started", result.message());
        assertEquals("{}", result.context());
    }

    @Test
    void applyForIngestBatchLoadsRulesOnceForTheWholeBatch() {
        Application application = application(APP_ID, ORG_ID);
        when(repository.findApplicableRules(ORG_ID, APP_ID))
                .thenReturn(List.of(rule(ORG_ID, null, "level:DEBUG", LogPipelineAction.DROP)));
        List<LogEventPayload> payloads = List.of(
                LogEventPayload.builder().level(LogLevel.DEBUG).message("cache miss").build(),
                LogEventPayload.builder().level(LogLevel.INFO).message("started").build());

        List<LogPipelineEngine.PipelineResult> results =
                service.applyForIngestBatch(application, payloads);

        assertEquals(2, results.size());
        assertTrue(results.get(0).dropped());
        assertFalse(results.get(1).dropped());
        verify(repository, times(1)).findApplicableRules(ORG_ID, APP_ID);
    }

    @Test
    void testEndpointRunsThePersistedRulesForTheOwnedApplication() {
        when(applicationService.getOwnedApplication(APP_ID, ORG_ID)).thenReturn(application(APP_ID, ORG_ID));
        when(repository.findApplicableRules(ORG_ID, APP_ID))
                .thenReturn(List.of(rule(ORG_ID, null, "level:DEBUG", LogPipelineAction.DROP)));

        LogPipelineTestResponse response = service.test(ORG_ID, TestLogPipelineRequest.builder()
                .applicationId(APP_ID)
                .level(LogLevel.DEBUG)
                .message("cache miss")
                .build());

        assertTrue(response.dropped());
        assertEquals(1, response.matchedRules().size());
        assertEquals(null, response.output());
    }

    @Test
    void testEndpointReturnsTheTransformedOutput() {
        when(applicationService.getOwnedApplication(APP_ID, ORG_ID)).thenReturn(application(APP_ID, ORG_ID));
        when(repository.findApplicableRules(ORG_ID, APP_ID))
                .thenReturn(List.of(rule(ORG_ID, null, "*", LogPipelineAction.ADD_ATTRIBUTE,
                        "{\"key\":\"env\",\"value\":\"prod\"}")));

        LogPipelineTestResponse response = service.test(ORG_ID, TestLogPipelineRequest.builder()
                .applicationId(APP_ID)
                .level(LogLevel.INFO)
                .message("started")
                .context("{\"service\":\"api\"}")
                .build());

        assertFalse(response.dropped());
        assertTrue(response.output().context().contains("\"env\":\"prod\""));
        assertTrue(response.output().context().contains("\"service\":\"api\""));
    }

    private Application application(Long id, Long organizationId) {
        return Application.builder()
                .id(id)
                .name("app-" + id)
                .apiKeyHash("hash-" + id)
                .organizationId(organizationId)
                .build();
    }

    private LogPipelineRule rule(Long organizationId, Long id) {
        return rule(organizationId, id, "*", LogPipelineAction.DROP);
    }

    private LogPipelineRule rule(Long organizationId, Long id, String filter, LogPipelineAction action) {
        return rule(organizationId, id, filter, action, "{}");
    }

    private LogPipelineRule rule(Long organizationId,
                                 Long id,
                                 String filter,
                                 LogPipelineAction action,
                                 String config) {
        LogPipelineRule rule = LogPipelineRule.builder()
                .organizationId(organizationId)
                .name("rule-" + id)
                .filterExpression(filter)
                .action(action)
                .actionConfig(config)
                .orderIndex(0)
                .build();
        rule.setId(id);
        return rule;
    }

    private CreateLogPipelineRuleRequest createRequest(String name,
                                                       Long applicationId,
                                                       String filter,
                                                       LogPipelineAction action,
                                                       String config) {
        return CreateLogPipelineRuleRequest.builder()
                .name(name)
                .applicationId(applicationId)
                .filter(filter)
                .action(action)
                .actionConfig(readConfig(config))
                .build();
    }

    private UpdateLogPipelineRuleRequest updateRequest(String name,
                                                       Long applicationId,
                                                       String filter,
                                                       LogPipelineAction action,
                                                       String config) {
        return UpdateLogPipelineRuleRequest.builder()
                .name(name)
                .applicationId(applicationId)
                .filter(filter)
                .action(action)
                .actionConfig(readConfig(config))
                .build();
    }

    private com.fasterxml.jackson.databind.JsonNode readConfig(String config) {
        try {
            return new ObjectMapper().readTree(config);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
