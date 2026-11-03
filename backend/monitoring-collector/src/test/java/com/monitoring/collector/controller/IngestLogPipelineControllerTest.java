package com.monitoring.collector.controller;

import com.monitoring.apps.entity.Application;
import com.monitoring.collector.dto.response.LogPipelineRuleResponse;
import com.monitoring.collector.entity.LogPipelineAction;
import com.monitoring.collector.entity.LogPipelineRule;
import com.monitoring.collector.service.LogPipelineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the agent-facing rule contract and that the application scope comes from the
 * authenticated API-key principal.
 */
class IngestLogPipelineControllerTest {

    private LogPipelineService logPipelineService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        logPipelineService = Mockito.mock(LogPipelineService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new IngestLogPipelineController(logPipelineService))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheRulesForTheAuthenticatedApplication() throws Exception {
        Application application = Application.builder().id(7L).organizationId(3L).build();
        LogPipelineRule rule = LogPipelineRule.builder().id(11L).build();
        Instant createdAt = Instant.parse("2026-10-29T00:00:00Z");
        when(logPipelineService.rulesForAgent(application)).thenReturn(List.of(rule));
        when(logPipelineService.toResponse(rule)).thenReturn(new LogPipelineRuleResponse(
                11L, 3L, 7L, "Drop debug", "level:DEBUG", LogPipelineAction.DROP,
                null, 0, List.of(), createdAt, createdAt));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(application, null, List.of()));

        mockMvc.perform(get("/api/v1/ingest/log-pipelines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].id").value(11))
                .andExpect(jsonPath("$.rules[0].applicationId").value(7))
                .andExpect(jsonPath("$.rules[0].action").value("DROP"))
                .andExpect(jsonPath("$.rules[0].filter").value("level:DEBUG"));

        verify(logPipelineService).rulesForAgent(application);
    }
}
