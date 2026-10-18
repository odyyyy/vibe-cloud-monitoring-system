package com.monitoring.collector.controller;

import com.monitoring.auth.organization.tenant.CurrentTenantService;
import com.monitoring.collector.dto.SavedLogViewQuery;
import com.monitoring.collector.dto.SavedLogViewResponse;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.exception.SavedLogViewAccessDeniedException;
import com.monitoring.collector.exception.SavedLogViewLimitExceededException;
import com.monitoring.collector.exception.SavedLogViewNotFoundException;
import com.monitoring.collector.service.SavedLogViewService;
import com.monitoring.storage.exception.ApplicationAccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SavedLogViewControllerTest {

    private static final Long USER_ID = 42L;
    private static final Long VIEW_ID = 5L;

    private static final String VALID_CREATE_BODY = """
            {"name":"Errors","queryParams":{"applicationId":7,"level":"ERROR","searchText":"boom",
            "timeRange":{"from":"2026-10-09T00:00:00Z","to":"2026-10-09T01:00:00Z"}}}
            """;

    private SavedLogViewService savedLogViewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        savedLogViewService = Mockito.mock(SavedLogViewService.class);
        CurrentTenantService tenantService = Mockito.mock(CurrentTenantService.class);
        when(tenantService.requireOrganizationId()).thenReturn(USER_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SavedLogViewController(savedLogViewService, tenantService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build()))
                .setControllerAdvice(new TestAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SavedLogViewResponse response() {
        return SavedLogViewResponse.builder()
                .id(VIEW_ID)
                .name("Errors")
                .queryParams(SavedLogViewQuery.builder()
                        .applicationId(7L)
                        .level(LogLevel.ERROR)
                        .searchText("boom")
                        .timeRange(SavedLogViewQuery.TimeRange.builder()
                                .from(Instant.parse("2026-10-09T00:00:00Z"))
                                .to(Instant.parse("2026-10-09T01:00:00Z"))
                                .build())
                        .build())
                .build();
    }

    @Test
    void list_returnsViews() throws Exception {
        when(savedLogViewService.list(USER_ID)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/saved-log-views"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(VIEW_ID))
                .andExpect(jsonPath("$[0].name").value("Errors"))
                .andExpect(jsonPath("$[0].queryParams.applicationId").value(7))
                .andExpect(jsonPath("$[0].queryParams.level").value("ERROR"))
                .andExpect(jsonPath("$[0].queryParams.searchTextSafe").doesNotExist())
                .andExpect(jsonPath("$[0].queryParams.timeRange.timeRangeValid").doesNotExist());
    }

    @Test
    void create_returnsCreatedPayload() throws Exception {
        when(savedLogViewService.create(eq(USER_ID), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/saved-log-views")
                        .contentType("application/json")
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(VIEW_ID));
    }

    @Test
    void create_missingQueryParams_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/saved-log-views")
                        .contentType("application/json")
                        .content("{\"name\":\"Errors\"}"))
                .andExpect(status().isBadRequest());

        verify(savedLogViewService, never()).create(any(), any());
    }

    @Test
    void create_searchTextWithAngleBrackets_returnsBadRequest() throws Exception {
        String body = VALID_CREATE_BODY.replace("boom", "<script>alert(1)</script>");

        mockMvc.perform(post("/api/v1/saved-log-views")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(savedLogViewService, never()).create(any(), any());
    }

    @Test
    void create_rangeExceedingLimit_returnsBadRequest() throws Exception {
        String body = VALID_CREATE_BODY
                .replace("2026-10-09T01:00:00Z", "2026-12-15T01:00:00Z");

        mockMvc.perform(post("/api/v1/saved-log-views")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(savedLogViewService, never()).create(any(), any());
    }

    @Test
    void create_forForeignApplication_returnsForbidden() throws Exception {
        when(savedLogViewService.create(eq(USER_ID), any()))
                .thenThrow(new ApplicationAccessDeniedException());

        mockMvc.perform(post("/api/v1/saved-log-views")
                        .contentType("application/json")
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_overQuota_returnsConflict() throws Exception {
        when(savedLogViewService.create(eq(USER_ID), any()))
                .thenThrow(new SavedLogViewLimitExceededException(50));

        mockMvc.perform(post("/api/v1/saved-log-views")
                        .contentType("application/json")
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void rename_returnsOk() throws Exception {
        when(savedLogViewService.rename(eq(USER_ID), eq(VIEW_ID), any())).thenReturn(response());

        mockMvc.perform(put("/api/v1/saved-log-views/" + VIEW_ID)
                        .contentType("application/json")
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Errors"));
    }

    @Test
    void rename_blankName_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/saved-log-views/" + VIEW_ID)
                        .contentType("application/json")
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verify(savedLogViewService, never()).rename(any(), any(), any());
    }

    @Test
    void rename_missingView_returnsNotFound() throws Exception {
        when(savedLogViewService.rename(eq(USER_ID), eq(VIEW_ID), any()))
                .thenThrow(new SavedLogViewNotFoundException(VIEW_ID));

        mockMvc.perform(put("/api/v1/saved-log-views/" + VIEW_ID)
                        .contentType("application/json")
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rename_foreignView_returnsForbidden() throws Exception {
        when(savedLogViewService.rename(eq(USER_ID), eq(VIEW_ID), any()))
                .thenThrow(new SavedLogViewAccessDeniedException());

        mockMvc.perform(put("/api/v1/saved-log-views/" + VIEW_ID)
                        .contentType("application/json")
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/saved-log-views/" + VIEW_ID))
                .andExpect(status().isNoContent());

        verify(savedLogViewService).delete(USER_ID, VIEW_ID);
    }

    @Test
    void delete_missingView_returnsNotFound() throws Exception {
        Mockito.doThrow(new SavedLogViewNotFoundException(VIEW_ID))
                .when(savedLogViewService).delete(USER_ID, VIEW_ID);

        mockMvc.perform(delete("/api/v1/saved-log-views/" + VIEW_ID))
                .andExpect(status().isNotFound());
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

        @ExceptionHandler(SavedLogViewNotFoundException.class)
        ResponseEntity<Map<String, Object>> handleNotFound(SavedLogViewNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", HttpStatus.NOT_FOUND.value(), "message", ex.getMessage()));
        }

        @ExceptionHandler({SavedLogViewAccessDeniedException.class, ApplicationAccessDeniedException.class})
        ResponseEntity<Map<String, Object>> handleForbidden(RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", HttpStatus.FORBIDDEN.value(), "message", ex.getMessage()));
        }

        @ExceptionHandler(SavedLogViewLimitExceededException.class)
        ResponseEntity<Map<String, Object>> handleConflict(SavedLogViewLimitExceededException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", HttpStatus.CONFLICT.value(), "message", ex.getMessage()));
        }
    }
}
