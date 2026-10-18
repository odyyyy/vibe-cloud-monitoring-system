package com.monitoring.collector.controller;

import com.monitoring.collector.dto.request.MetricIngestRequest;
import com.monitoring.collector.service.MetricIngestService;
import com.monitoring.apps.entity.Application;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetricIngestControllerTest {

    private static final Long APP_ID = 7L;

    private MetricIngestService metricIngestService;
    private MockMvc mockMvc;
    private Application application;

    @BeforeEach
    void setUp() {
        metricIngestService = Mockito.mock(MetricIngestService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MetricIngestController(metricIngestService))
                .setControllerAdvice(new TestValidationAdvice())
                .build();

        application = Application.builder()
                .id(APP_ID)
                .name("my-app")
                .apiKeyHash("app_key_hash")
                .organizationId(1L)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        application, null, List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validPayload_returnsAcceptedAndDelegatesToService() throws Exception {
        when(metricIngestService.ingest(eq(application), any(MetricIngestRequest.class))).thenReturn(1);

        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"name\":\"cpu_usage\",\"value\":75.5,"
                                + "\"timestamp\":\"2026-10-09T10:00:00Z\",\"tags\":{\"host\":\"server-1\"}}]}"))
                .andExpect(status().isAccepted());

        verify(metricIngestService).ingest(eq(application), any(MetricIngestRequest.class));
    }

    @Test
    void blankName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"name\":\"\",\"value\":1.0,"
                                + "\"timestamp\":\"2026-10-09T10:00:00Z\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[\"metrics[0].name\"]").exists());

        verify(metricIngestService, Mockito.never()).ingest(any(), any());
    }

    @Test
    void missingTimestamp_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"name\":\"cpu_usage\",\"value\":1.0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[\"metrics[0].timestamp\"]").exists());
    }

    @Test
    void emptyMetrics_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.metrics").exists());
    }

    @Test
    void tooManyMetrics_returnsBadRequest() throws Exception {
        StringBuilder body = new StringBuilder("{\"metrics\":[");
        for (int i = 0; i < MetricIngestRequest.MAX_BATCH_SIZE + 1; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"name\":\"m\",\"value\":1.0,\"timestamp\":\"2026-10-09T10:00:00Z\"}");
        }
        body.append("]}");

        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.metrics").exists());

        verify(metricIngestService, Mockito.never()).ingest(any(), any());
    }

    @Test
    void oversizedTagKey_returnsBadRequest() throws Exception {
        String tagKey = "k".repeat(101);
        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"name\":\"cpu_usage\",\"value\":1.0,"
                                + "\"timestamp\":\"2026-10-09T10:00:00Z\",\"tags\":{\"" + tagKey + "\":\"v\"}}]}"))
                .andExpect(status().isBadRequest());

        verify(metricIngestService, Mockito.never()).ingest(any(), any());
    }

    @Test
    void oversizedTagValue_returnsBadRequest() throws Exception {
        String tagValue = "v".repeat(501);
        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"name\":\"cpu_usage\",\"value\":1.0,"
                                + "\"timestamp\":\"2026-10-09T10:00:00Z\",\"tags\":{\"host\":\"" + tagValue + "\"}}]}"))
                .andExpect(status().isBadRequest());

        verify(metricIngestService, Mockito.never()).ingest(any(), any());
    }

    @Test
    void nonFiniteValue_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"name\":\"cpu_usage\",\"value\":NaN,"
                                + "\"timestamp\":\"2026-10-09T10:00:00Z\"}]}"))
                .andExpect(status().isBadRequest());

        verify(metricIngestService, Mockito.never()).ingest(any(), any());
    }

    /**
     * Minimal advice for the standalone test: in runtime the same errors are handled by the
     * global ApiExceptionHandler from monitoring-api.
     */
    @RestControllerAdvice
    static class TestValidationAdvice {

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
    }
}
