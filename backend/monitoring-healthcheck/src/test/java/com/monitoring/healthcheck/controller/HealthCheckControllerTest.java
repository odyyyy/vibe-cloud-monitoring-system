package com.monitoring.healthcheck.controller;

import com.monitoring.auth.organization.tenant.CurrentTenantService;
import com.monitoring.healthcheck.dto.request.CreateHealthCheckRequest;
import com.monitoring.healthcheck.dto.response.HealthCheckResponse;
import com.monitoring.healthcheck.dto.response.HealthCheckResultResponse;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.exception.HealthCheckAccessDeniedException;
import com.monitoring.healthcheck.exception.HealthCheckNotFoundException;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import com.monitoring.healthcheck.service.HealthCheckService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

class HealthCheckControllerTest {

    private static final Long USER_ID = 42L;

    private HealthCheckService healthCheckService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        healthCheckService = Mockito.mock(HealthCheckService.class);
        CurrentTenantService tenantService = Mockito.mock(CurrentTenantService.class);
        when(tenantService.requireOrganizationId()).thenReturn(USER_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthCheckController(healthCheckService, tenantService))
                .setControllerAdvice(new TestAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private HealthCheckResponse sampleCheck() {
        return HealthCheckResponse.builder()
                .id(5L)
                .organizationId(USER_ID)
                .name("API health")
                .targetUrl("https://example.com/health")
                .method(HealthCheckMethod.GET)
                .expectedStatusCode(200)
                .intervalSeconds(60)
                .active(true)
                .build();
    }

    @Test
    void list_returnsOwnChecks() throws Exception {
        when(healthCheckService.list(USER_ID)).thenReturn(List.of(sampleCheck()));

        mockMvc.perform(get("/api/v1/health-checks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].organizationId").value(USER_ID))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    void create_valid_returnsCreatedWithLocation() throws Exception {
        when(healthCheckService.create(eq(USER_ID), any(CreateHealthCheckRequest.class)))
                .thenReturn(sampleCheck());

        mockMvc.perform(post("/api/v1/health-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API health\",\"targetUrl\":\"https://example.com/health\","
                                + "\"method\":\"GET\",\"expectedStatusCode\":200,\"intervalSeconds\":60,"
                                + "\"isActive\":true}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/health-checks/5"));

        ArgumentCaptor<CreateHealthCheckRequest> captor = ArgumentCaptor.forClass(CreateHealthCheckRequest.class);
        verify(healthCheckService).create(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getActive()).isTrue();
        assertThat(captor.getValue().getMethod()).isEqualTo(HealthCheckMethod.GET);
    }

    @Test
    void create_blankName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/health-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"targetUrl\":\"https://example.com\","
                                + "\"method\":\"GET\",\"expectedStatusCode\":200,\"intervalSeconds\":60}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        verify(healthCheckService, Mockito.never()).create(any(), any());
    }

    @Test
    void create_statusCodeOutOfRange_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/health-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API\",\"targetUrl\":\"https://example.com\","
                                + "\"method\":\"GET\",\"expectedStatusCode\":50,\"intervalSeconds\":60}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.expectedStatusCode").exists());
    }

    @Test
    void create_intervalTooSmall_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/health-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API\",\"targetUrl\":\"https://example.com\","
                                + "\"method\":\"GET\",\"expectedStatusCode\":200,\"intervalSeconds\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.intervalSeconds").exists());
    }

    @Test
    void create_blockedTarget_returnsBadRequest() throws Exception {
        when(healthCheckService.create(eq(USER_ID), any(CreateHealthCheckRequest.class)))
                .thenThrow(new InvalidTargetUrlException("Target host resolves to a non-public address"));

        mockMvc.perform(post("/api/v1/health-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API\",\"targetUrl\":\"http://127.0.0.1\","
                                + "\"method\":\"GET\",\"expectedStatusCode\":200,\"intervalSeconds\":60}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_partialPayload_returnsOk() throws Exception {
        when(healthCheckService.update(eq(USER_ID), eq(5L), any())).thenReturn(sampleCheck());

        mockMvc.perform(put("/api/v1/health-checks/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void update_anotherUser_returnsForbidden() throws Exception {
        when(healthCheckService.update(eq(USER_ID), eq(5L), any()))
                .thenThrow(new HealthCheckAccessDeniedException());

        mockMvc.perform(put("/api/v1/health-checks/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_missing_returnsNotFound() throws Exception {
        when(healthCheckService.update(eq(USER_ID), eq(99L), any()))
                .thenThrow(new HealthCheckNotFoundException(99L));

        mockMvc.perform(put("/api/v1/health-checks/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_success_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/health-checks/5")).andExpect(status().isNoContent());
        verify(healthCheckService).delete(USER_ID, 5L);
    }

    @Test
    void results_forwardsLimitAndReturnsHistory() throws Exception {
        HealthCheckResultResponse result = HealthCheckResultResponse.builder()
                .id(1L)
                .configId(5L)
                .status(HealthStatus.UP)
                .responseTimeMs(123)
                .errorMessage(null)
                .checkedAt(Instant.parse("2026-10-16T10:00:00Z"))
                .build();
        when(healthCheckService.listResults(USER_ID, 5L, 25)).thenReturn(List.of(result));

        mockMvc.perform(get("/api/v1/health-checks/5/results").param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("UP"))
                .andExpect(jsonPath("$[0].configId").value(5))
                .andExpect(jsonPath("$[0].responseTimeMs").value(123));
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

        @ExceptionHandler(HealthCheckNotFoundException.class)
        ResponseEntity<Map<String, Object>> handleNotFound(HealthCheckNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", HttpStatus.NOT_FOUND.value(), "message", ex.getMessage()));
        }

        @ExceptionHandler(HealthCheckAccessDeniedException.class)
        ResponseEntity<Map<String, Object>> handleForbidden(HealthCheckAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", 403));
        }

        @ExceptionHandler(InvalidTargetUrlException.class)
        ResponseEntity<Map<String, Object>> handleInvalidTarget(InvalidTargetUrlException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", HttpStatus.BAD_REQUEST.value(), "message", ex.getMessage()));
        }
    }
}
