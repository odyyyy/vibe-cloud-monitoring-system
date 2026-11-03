package com.monitoring.collector.controller;

import com.monitoring.apps.entity.Application;
import com.monitoring.collector.dto.request.AgentConfigStatusRequest;
import com.monitoring.collector.dto.response.ActiveAgentConfigResponse;
import com.monitoring.collector.service.AgentConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentConfigAgentControllerTest {

    private AgentConfigService service;
    private MockMvc mockMvc;
    private Application application;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(AgentConfigService.class);
        application = Application.builder().id(7L).organizationId(3L).build();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentConfigAgentController(service))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(application, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeReturnsTheConfigForTheAuthenticatedApplication() throws Exception {
        when(service.activeConfig(application))
                .thenReturn(new ActiveAgentConfigResponse("abc123", "interval_seconds: 30\n"));

        mockMvc.perform(get("/api/v1/agent-configs/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configVersion").value("abc123"))
                .andExpect(jsonPath("$.configYaml").value("interval_seconds: 30\n"));

        verify(service).activeConfig(application);
    }

    @Test
    void statusStoresTheReportedConfigState() throws Exception {
        mockMvc.perform(post("/api/v1/agent-configs/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hostname": "server-1",
                                  "configVersion": "abc123",
                                  "status": "ERROR",
                                  "errorMessage": "yaml: line 1: did not find expected node content"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(service).reportStatus(any(Application.class), any(AgentConfigStatusRequest.class));
    }
}
