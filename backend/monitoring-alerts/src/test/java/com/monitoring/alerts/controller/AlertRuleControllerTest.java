package com.monitoring.alerts.controller;

import com.monitoring.auth.organization.tenant.CurrentTenantService;
import com.monitoring.alerts.dto.AlertRuleResponse;
import com.monitoring.alerts.dto.CreateAlertRuleRequest;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.exception.AlertRuleAccessDeniedException;
import com.monitoring.alerts.exception.AlertRuleNotFoundException;
import com.monitoring.alerts.exception.InvalidAlertRuleException;
import com.monitoring.alerts.service.AlertService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertRuleControllerTest {

    private static final Long USER_ID = 42L;

    private AlertService alertService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        alertService = Mockito.mock(AlertService.class);
        CurrentTenantService tenantService = Mockito.mock(CurrentTenantService.class);
        when(tenantService.requireOrganizationId()).thenReturn(USER_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AlertRuleController(alertService, tenantService))
                .setControllerAdvice(new TestAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AlertRuleResponse sampleRule() {
        return AlertRuleResponse.builder()
                .id(5L)
                .organizationId(USER_ID)
                .applicationId(7L)
                .name("High CPU")
                .type(AlertRuleType.METRIC_THRESHOLD)
                .target("system.cpu.usage")
                .condition(AlertCondition.GREATER_THAN)
                .threshold(90.0)
                .durationMinutes(5)
                .active(true)
                .build();
    }

    @Test
    void list_returnsOwnRules() throws Exception {
        when(alertService.listRules(USER_ID)).thenReturn(List.of(sampleRule()));

        mockMvc.perform(get("/api/v1/alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].isActive").value(true))
                .andExpect(jsonPath("$[0].condition").value("GREATER_THAN"));
    }

    @Test
    void create_valid_returnsCreatedWithLocation() throws Exception {
        when(alertService.createRule(eq(USER_ID), any(CreateAlertRuleRequest.class))).thenReturn(sampleRule());

        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"High CPU\",\"applicationId\":7,\"type\":\"METRIC_THRESHOLD\","
                                + "\"target\":\"system.cpu.usage\",\"condition\":\"GREATER_THAN\",\"threshold\":90.0,"
                                + "\"durationMinutes\":5,\"isActive\":true}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/alert-rules/5"));
    }

    @Test
    void create_blankName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"type\":\"METRIC_THRESHOLD\",\"target\":\"cpu\","
                                + "\"condition\":\"GREATER_THAN\",\"threshold\":90.0,\"durationMinutes\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        verify(alertService, Mockito.never()).createRule(any(), any());
    }

    @Test
    void create_durationOutOfRange_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CPU\",\"type\":\"METRIC_THRESHOLD\",\"target\":\"cpu\","
                                + "\"condition\":\"GREATER_THAN\",\"threshold\":90.0,\"durationMinutes\":5000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").exists());
    }

    @Test
    void create_inconsistentRule_returnsBadRequest() throws Exception {
        when(alertService.createRule(eq(USER_ID), any(CreateAlertRuleRequest.class)))
                .thenThrow(new InvalidAlertRuleException("A metric-threshold rule requires an application"));

        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CPU\",\"type\":\"METRIC_THRESHOLD\",\"target\":\"cpu\","
                                + "\"condition\":\"GREATER_THAN\",\"threshold\":90.0,\"durationMinutes\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_anotherUser_returnsForbidden() throws Exception {
        when(alertService.updateRule(eq(USER_ID), eq(5L), any()))
                .thenThrow(new AlertRuleAccessDeniedException());

        mockMvc.perform(put("/api/v1/alert-rules/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_missing_returnsNotFound() throws Exception {
        when(alertService.updateRule(eq(USER_ID), eq(99L), any()))
                .thenThrow(new AlertRuleNotFoundException(99L));

        mockMvc.perform(put("/api/v1/alert-rules/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_success_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/alert-rules/5")).andExpect(status().isNoContent());
        verify(alertService).deleteRule(USER_ID, 5L);
    }

    @RestControllerAdvice
    static class TestAdvice {

        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            ex.getBindingResult().getFieldErrors()
                    .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", HttpStatus.BAD_REQUEST.value());
            body.put("message", "Validation failed");
            body.put("fieldErrors", fieldErrors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        @ExceptionHandler(AlertRuleNotFoundException.class)
        ResponseEntity<Map<String, Object>> handleNotFound(AlertRuleNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", HttpStatus.NOT_FOUND.value(), "message", ex.getMessage()));
        }

        @ExceptionHandler(AlertRuleAccessDeniedException.class)
        ResponseEntity<Map<String, Object>> handleForbidden(AlertRuleAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", 403));
        }

        @ExceptionHandler(InvalidAlertRuleException.class)
        ResponseEntity<Map<String, Object>> handleInvalid(InvalidAlertRuleException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", HttpStatus.BAD_REQUEST.value(), "message", ex.getMessage()));
        }
    }
}
