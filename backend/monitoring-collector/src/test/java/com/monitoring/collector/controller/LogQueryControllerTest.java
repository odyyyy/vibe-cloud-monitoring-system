package com.monitoring.collector.controller;

import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.collector.dto.response.LogEventResponse;
import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.service.LogQueryService;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.exception.ApplicationNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogQueryControllerTest {

    private static final Long USER_ID = 42L;

    private static final String VALID_QUERY =
            "/api/v1/logs?applicationId=7&from=2026-10-09T00:00:00Z&to=2026-10-09T01:00:00Z"
                    + "&level=ERROR&page=0&size=25";

    private LogQueryService logQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        logQueryService = Mockito.mock(LogQueryService.class);
        CurrentTenantService tenantService = Mockito.mock(CurrentTenantService.class);
        when(tenantService.requireOrganizationId()).thenReturn(USER_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LogQueryController(logQueryService, tenantService))
                .setControllerAdvice(new TestAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validQuery_returnsPagedPayload() throws Exception {
        when(logQueryService.query(eq(USER_ID), any(LogsQueryRequest.class)))
                .thenReturn(new PageResponse<>(
                        List.of(LogEventResponse.builder()
                                .id(1L)
                                .applicationId(7L)
                                .timestamp(Instant.parse("2026-10-09T00:10:00Z"))
                                .level(LogLevel.ERROR)
                                .message("boom")
                                .build()),
                        1,
                        1,
                        0,
                        25));

        mockMvc.perform(get(VALID_QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].level").value("ERROR"))
                .andExpect(jsonPath("$.content[0].message").value("boom"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25));
    }

    @Test
    void missingApplicationId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/logs?from=2026-10-09T00:00:00Z&to=2026-10-09T01:00:00Z"))
                .andExpect(status().isBadRequest());

        verify(logQueryService, Mockito.never()).query(any(), any());
    }

    @Test
    void toBeforeFrom_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/logs?applicationId=7&from=2026-10-09T01:00:00Z&to=2026-10-09T00:00:00Z"))
                .andExpect(status().isBadRequest());

        verify(logQueryService, Mockito.never()).query(any(), any());
    }

    @Test
    void sizeAboveLimit_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/logs?applicationId=7&from=2026-10-09T00:00:00Z&to=2026-10-09T01:00:00Z&size=101"))
                .andExpect(status().isBadRequest());

        verify(logQueryService, Mockito.never()).query(any(), any());
    }

    @Test
    void negativePage_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/logs?applicationId=7&from=2026-10-09T00:00:00Z&to=2026-10-09T01:00:00Z&page=-1"))
                .andExpect(status().isBadRequest());

        verify(logQueryService, Mockito.never()).query(any(), any());
    }

    @Test
    void queryingForeignApplication_returnsForbidden() throws Exception {
        when(logQueryService.query(eq(USER_ID), any(LogsQueryRequest.class)))
                .thenThrow(new ApplicationAccessDeniedException());

        mockMvc.perform(get(VALID_QUERY)).andExpect(status().isForbidden());
    }

    @Test
    void queryingUnknownApplication_returnsNotFound() throws Exception {
        when(logQueryService.query(eq(USER_ID), any(LogsQueryRequest.class)))
                .thenThrow(new ApplicationNotFoundException(7L));

        mockMvc.perform(get(VALID_QUERY)).andExpect(status().isNotFound());
    }

    /**
     * Minimal advice for the standalone test: in the running application the same errors are
     * handled by the global ApiExceptionHandler from monitoring-api.
     */
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

        @ExceptionHandler(ApplicationAccessDeniedException.class)
        ResponseEntity<Map<String, Object>> handleForbidden(ApplicationAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", HttpStatus.FORBIDDEN.value(), "message", ex.getMessage()));
        }

        @ExceptionHandler(ApplicationNotFoundException.class)
        ResponseEntity<Map<String, Object>> handleNotFound(ApplicationNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", HttpStatus.NOT_FOUND.value(), "message", ex.getMessage()));
        }
    }
}
