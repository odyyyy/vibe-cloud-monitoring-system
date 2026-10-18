package com.monitoring.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import com.monitoring.api.exception.ApiExceptionHandler;
import com.monitoring.apps.dto.AgentNodeResponse;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.service.AgentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerTest {

    private static final Long USER_ID = 42L;

    private AgentService agentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        agentService = Mockito.mock(AgentService.class);
        // Mirror the application's Jackson setup so instants serialize as ISO-8601, not epoch.
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        CurrentTenantService tenantService = Mockito.mock(CurrentTenantService.class);
        when(tenantService.requireOrganizationId()).thenReturn(USER_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentController(agentService, tenantService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listAgents_returnsFleetForOwnedApplication() throws Exception {
        when(agentService.listForApplication(10L, USER_ID)).thenReturn(List.of(
                new AgentNodeResponse(1L, "server-1", "0.1.0", Instant.parse("2026-10-18T12:00:00Z"))));

        mockMvc.perform(get("/api/applications/10/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hostname").value("server-1"))
                .andExpect(jsonPath("$[0].agentVersion").value("0.1.0"))
                .andExpect(jsonPath("$[0].lastSeenAt").value("2026-10-18T12:00:00Z"));
    }

    @Test
    void listAgents_wrongOwner_returns403() throws Exception {
        when(agentService.listForApplication(10L, USER_ID)).thenThrow(new ApplicationAccessDeniedException());

        mockMvc.perform(get("/api/applications/10/agents"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
