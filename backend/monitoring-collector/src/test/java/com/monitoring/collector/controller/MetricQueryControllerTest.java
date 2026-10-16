package com.monitoring.collector.controller;

import com.monitoring.collector.dto.AggregatedMetricResponse;
import com.monitoring.collector.dto.MetricsQueryRequest;
import com.monitoring.collector.service.MetricQueryService;
import com.monitoring.storage.exception.ApplicationAccessDeniedException;
import com.monitoring.storage.exception.ApplicationNotFoundException;
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

class MetricQueryControllerTest {

    private static final Long USER_ID = 42L;

    private static final String VALID_QUERY =
            "/api/v1/metrics/query?applicationId=7&metricName=cpu_usage"
                    + "&from=2026-10-09T00:00:00Z&to=2026-10-09T01:00:00Z"
                    + "&aggregation=AVG&intervalMinutes=5";

    private MetricQueryService metricQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        metricQueryService = Mockito.mock(MetricQueryService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MetricQueryController(metricQueryService))
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
    void validQuery_returnsOkWithAggregatedPoints() throws Exception {
        when(metricQueryService.query(eq(USER_ID), any(MetricsQueryRequest.class)))
                .thenReturn(List.of(AggregatedMetricResponse.builder()
                        .timestamp(Instant.parse("2026-10-09T00:00:00Z"))
                        .value(15.0)
                        .build()));

        mockMvc.perform(get(VALID_QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(15.0))
                .andExpect(jsonPath("$[0].timestamp").exists());
    }

    @Test
    void toBeforeFrom_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/query?applicationId=7&metricName=cpu_usage"
                + "&from=2026-10-09T01:00:00Z&to=2026-10-09T00:00:00Z"
                + "&aggregation=AVG&intervalMinutes=5"))
                .andExpect(status().isBadRequest());

        verify(metricQueryService, Mockito.never()).query(any(), any());
    }

    @Test
    void rangeLongerThanThirtyDays_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/query?applicationId=7&metricName=cpu_usage"
                + "&from=2026-01-01T00:00:00Z&to=2026-03-15T00:00:00Z"
                + "&aggregation=AVG&intervalMinutes=5"))
                .andExpect(status().isBadRequest());

        verify(metricQueryService, Mockito.never()).query(any(), any());
    }

    @Test
    void intervalMinutesNotPositive_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/query?applicationId=7&metricName=cpu_usage"
                + "&from=2026-10-09T00:00:00Z&to=2026-10-09T01:00:00Z"
                + "&aggregation=AVG&intervalMinutes=0"))
                .andExpect(status().isBadRequest());

        verify(metricQueryService, Mockito.never()).query(any(), any());
    }

    @Test
    void missingApplicationId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/query?metricName=cpu_usage"
                + "&from=2026-10-09T00:00:00Z&to=2026-10-09T01:00:00Z"
                + "&aggregation=AVG&intervalMinutes=5"))
                .andExpect(status().isBadRequest());

        verify(metricQueryService, Mockito.never()).query(any(), any());
    }

    @Test
    void queryingForeignApplication_returnsForbidden() throws Exception {
        when(metricQueryService.query(eq(USER_ID), any(MetricsQueryRequest.class)))
                .thenThrow(new ApplicationAccessDeniedException());

        mockMvc.perform(get(VALID_QUERY))
                .andExpect(status().isForbidden());
    }

    @Test
    void queryingUnknownApplication_returnsNotFound() throws Exception {
        when(metricQueryService.query(eq(USER_ID), any(MetricsQueryRequest.class)))
                .thenThrow(new ApplicationNotFoundException(7L));

        mockMvc.perform(get(VALID_QUERY))
                .andExpect(status().isNotFound());
    }

    @Test
    void names_returnsOkWithDistinctMetricNames() throws Exception {
        when(metricQueryService.listMetricNames(eq(USER_ID), eq(7L)))
                .thenReturn(List.of("cpu_usage", "mem_usage"));

        mockMvc.perform(get("/api/v1/metrics/names?applicationId=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("cpu_usage"))
                .andExpect(jsonPath("$[1]").value("mem_usage"));
    }

    @Test
    void names_missingApplicationId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/names"))
                .andExpect(status().isBadRequest());

        verify(metricQueryService, Mockito.never()).listMetricNames(any(), any());
    }

    @Test
    void names_nonPositiveApplicationId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/names?applicationId=0"))
                .andExpect(status().isBadRequest());

        verify(metricQueryService, Mockito.never()).listMetricNames(any(), any());
    }

    @Test
    void names_foreignApplication_returnsForbidden() throws Exception {
        when(metricQueryService.listMetricNames(eq(USER_ID), eq(7L)))
                .thenThrow(new ApplicationAccessDeniedException());

        mockMvc.perform(get("/api/v1/metrics/names?applicationId=7"))
                .andExpect(status().isForbidden());
    }

    @Test
    void names_unknownApplication_returnsNotFound() throws Exception {
        when(metricQueryService.listMetricNames(eq(USER_ID), eq(7L)))
                .thenThrow(new ApplicationNotFoundException(7L));

        mockMvc.perform(get("/api/v1/metrics/names?applicationId=7"))
                .andExpect(status().isNotFound());
    }

    /**
     * Minimal advice for the standalone test: in the running application the same
     * errors are handled by the global ApiExceptionHandler from monitoring-api.
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
