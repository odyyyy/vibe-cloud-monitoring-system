package com.monitoring.collector.controller;

import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.collector.dto.response.LogFacetValueResponse;
import com.monitoring.collector.dto.response.LogFacetsResponse;
import com.monitoring.collector.exception.InvalidLogFacetRequestException;
import com.monitoring.collector.exception.LogFacetRateLimitedException;
import com.monitoring.collector.exception.LogFacetTimeoutException;
import com.monitoring.collector.facet.LogFacetField;
import com.monitoring.collector.service.LogFacetService;
import com.monitoring.organization.tenant.CurrentTenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the HTTP contract of the facet endpoint: the documented response shape, the field and
 * limit validation, and the mapping of the aggregation failures to 429 and 503 so the log explorer
 * can keep working without facets.
 */
class LogFacetControllerTest {

    private static final Long USER_ID = 42L;

    private static final String VALID_QUERY =
            "/api/v1/logs/facets?applicationId=7&from=2026-10-09T00:00:00Z&to=2026-10-09T01:00:00Z"
                    + "&fields=level,host&limit=5";

    private LogFacetService logFacetService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        logFacetService = Mockito.mock(LogFacetService.class);
        CurrentTenantService tenantService = Mockito.mock(CurrentTenantService.class);
        when(tenantService.requireOrganizationId()).thenReturn(USER_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LogFacetController(logFacetService, tenantService))
                .setControllerAdvice(new TestAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static LogFacetsResponse response() {
        Map<String, List<LogFacetValueResponse>> facets = new LinkedHashMap<>();
        facets.put("level", List.of(new LogFacetValueResponse("ERROR", 3)));
        facets.put("host", List.of(
                new LogFacetValueResponse("server-1", 2),
                new LogFacetValueResponse("server-2", 1)));
        return new LogFacetsResponse(facets, false, 50_000);
    }

    @Test
    void validRequest_returnsFacetPayload() throws Exception {
        when(logFacetService.facets(eq(USER_ID), any(LogsQueryRequest.class),
                eq(List.of(LogFacetField.LEVEL, LogFacetField.HOST)), eq(5)))
                .thenReturn(response());

        mockMvc.perform(get(VALID_QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facets.level[0].value").value("ERROR"))
                .andExpect(jsonPath("$.facets.level[0].count").value(3))
                .andExpect(jsonPath("$.facets.host[0].value").value("server-1"))
                .andExpect(jsonPath("$.facets.host[1].count").value(1))
                .andExpect(jsonPath("$.approximate").value(false))
                .andExpect(jsonPath("$.scanLimit").value(50_000));
    }

    @Test
    void appliesTheDefaultLimitWhenNoneIsGiven() throws Exception {
        when(logFacetService.facets(eq(USER_ID), any(LogsQueryRequest.class),
                eq(List.of(LogFacetField.LEVEL)), anyInt()))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/logs/facets?applicationId=7"
                        + "&from=2026-10-09T00:00:00Z&to=2026-10-09T01:00:00Z&fields=level"))
                .andExpect(status().isOk());

        verify(logFacetService).facets(eq(USER_ID), any(LogsQueryRequest.class),
                eq(List.of(LogFacetField.LEVEL)), eq(LogFacetService.DEFAULT_LIMIT));
    }

    @Test
    void missingFields_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/logs/facets?applicationId=7"
                        + "&from=2026-10-09T00:00:00Z&to=2026-10-09T01:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("fields")));

        verify(logFacetService, Mockito.never()).facets(any(), any(), any(), anyInt());
    }

    @Test
    void unknownField_returnsBadRequest() throws Exception {
        mockMvc.perform(get(VALID_QUERY.replace("level,host", "level,password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("password")));

        verify(logFacetService, Mockito.never()).facets(any(), any(), any(), anyInt());
    }

    @Test
    void limitBelowRange_returnsBadRequest() throws Exception {
        mockMvc.perform(get(VALID_QUERY.replace("limit=5", "limit=0")))
                .andExpect(status().isBadRequest());

        verify(logFacetService, Mockito.never()).facets(any(), any(), any(), anyInt());
    }

    @Test
    void limitAboveRange_returnsBadRequest() throws Exception {
        mockMvc.perform(get(VALID_QUERY.replace("limit=5", "limit=11")))
                .andExpect(status().isBadRequest());

        verify(logFacetService, Mockito.never()).facets(any(), any(), any(), anyInt());
    }

    @Test
    void missingApplicationId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/logs/facets?from=2026-10-09T00:00:00Z"
                        + "&to=2026-10-09T01:00:00Z&fields=level"))
                .andExpect(status().isBadRequest());

        verify(logFacetService, Mockito.never()).facets(any(), any(), any(), anyInt());
    }

    @Test
    void tooLongHostFilter_returnsBadRequest() throws Exception {
        mockMvc.perform(get(VALID_QUERY + "&host=" + "a".repeat(257)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.host").exists());

        verify(logFacetService, Mockito.never()).facets(any(), any(), any(), anyInt());
    }

    @Test
    void forwardsTheFacetFiltersToTheService() throws Exception {
        when(logFacetService.facets(eq(USER_ID), any(LogsQueryRequest.class),
                eq(List.of(LogFacetField.HOST)), eq(5)))
                .thenReturn(response());

        mockMvc.perform(get(VALID_QUERY.replace("level,host", "host")
                        + "&host=server-1&service=api&namespace=prod"))
                .andExpect(status().isOk());

        verify(logFacetService).facets(eq(USER_ID),
                org.mockito.ArgumentMatchers.argThat(request ->
                        "server-1".equals(request.getHost())
                                && "api".equals(request.getService())
                                && "prod".equals(request.getNamespace())),
                eq(List.of(LogFacetField.HOST)), eq(5));
    }

    @Test
    void foreignApplication_returnsForbidden() throws Exception {
        when(logFacetService.facets(any(), any(), any(), anyInt()))
                .thenThrow(new ApplicationAccessDeniedException());

        mockMvc.perform(get(VALID_QUERY)).andExpect(status().isForbidden());
    }

    @Test
    void rateLimited_returnsTooManyRequestsWithRetryAfter() throws Exception {
        when(logFacetService.facets(any(), any(), any(), anyInt()))
                .thenThrow(new LogFacetRateLimitedException(12));

        mockMvc.perform(get(VALID_QUERY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "12"));
    }

    @Test
    void timeout_returnsServiceUnavailable() throws Exception {
        when(logFacetService.facets(any(), any(), any(), anyInt()))
                .thenThrow(new LogFacetTimeoutException());

        mockMvc.perform(get(VALID_QUERY)).andExpect(status().isServiceUnavailable());
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

        @ExceptionHandler(InvalidLogFacetRequestException.class)
        ResponseEntity<Map<String, Object>> handleInvalidFacetRequest(InvalidLogFacetRequestException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", HttpStatus.BAD_REQUEST.value(), "message", ex.getMessage()));
        }

        @ExceptionHandler(ApplicationAccessDeniedException.class)
        ResponseEntity<Map<String, Object>> handleForbidden(ApplicationAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", HttpStatus.FORBIDDEN.value(), "message", ex.getMessage()));
        }

        @ExceptionHandler(LogFacetRateLimitedException.class)
        ResponseEntity<Map<String, Object>> handleRateLimited(LogFacetRateLimitedException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                    .body(Map.of("status", HttpStatus.TOO_MANY_REQUESTS.value(),
                            "message", ex.getMessage()));
        }

        @ExceptionHandler(LogFacetTimeoutException.class)
        ResponseEntity<Map<String, Object>> handleTimeout(LogFacetTimeoutException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                            "message", ex.getMessage()));
        }
    }
}
