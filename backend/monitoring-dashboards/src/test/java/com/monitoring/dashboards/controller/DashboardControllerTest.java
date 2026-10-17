package com.monitoring.dashboards.controller;

import com.monitoring.dashboards.dto.CreateDashboardRequest;
import com.monitoring.dashboards.dto.DashboardResponse;
import com.monitoring.dashboards.dto.UpdateWidgetRequest;
import com.monitoring.dashboards.dto.WidgetResponse;
import com.monitoring.dashboards.entity.WidgetType;
import com.monitoring.dashboards.exception.DashboardAccessDeniedException;
import com.monitoring.dashboards.exception.DashboardNotFoundException;
import com.monitoring.dashboards.exception.InvalidWidgetConfigException;
import com.monitoring.dashboards.service.DashboardService;
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

import java.time.Instant;
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

class DashboardControllerTest {

    private static final Long USER_ID = 42L;

    private DashboardService dashboardService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dashboardService = Mockito.mock(DashboardService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(dashboardService))
                .setControllerAdvice(new TestAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private DashboardResponse sampleDashboard() {
        return DashboardResponse.builder()
                .id(5L)
                .name("Prod")
                .userId(USER_ID)
                .createdAt(Instant.parse("2026-10-09T10:00:00Z"))
                .widgets(List.of())
                .build();
    }

    private WidgetResponse sampleWidget() {
        return WidgetResponse.builder()
                .id(3L)
                .dashboardId(5L)
                .type(WidgetType.LOG_TABLE)
                .config("{\"applicationId\":1}")
                .positionX(0)
                .positionY(0)
                .width(6)
                .height(8)
                .build();
    }

    @Test
    void listDashboards_returnsOwnDashboards() throws Exception {
        when(dashboardService.listDashboards(USER_ID)).thenReturn(List.of(sampleDashboard()));

        mockMvc.perform(get("/api/v1/dashboards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].userId").value(USER_ID));
    }

    @Test
    void createDashboard_valid_returnsCreatedWithLocation() throws Exception {
        when(dashboardService.createDashboard(eq(USER_ID), any(CreateDashboardRequest.class)))
                .thenReturn(sampleDashboard());

        mockMvc.perform(post("/api/v1/dashboards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Prod\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/dashboards/5"));
    }

    @Test
    void createDashboard_blankName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/dashboards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        verify(dashboardService, Mockito.never()).createDashboard(any(), any());
    }

    @Test
    void getDashboard_anotherUser_returnsForbidden() throws Exception {
        when(dashboardService.getDashboard(USER_ID, 5L)).thenThrow(new DashboardAccessDeniedException());

        mockMvc.perform(get("/api/v1/dashboards/5")).andExpect(status().isForbidden());
    }

    @Test
    void getDashboard_missing_returnsNotFound() throws Exception {
        when(dashboardService.getDashboard(USER_ID, 99L)).thenThrow(new DashboardNotFoundException(99L));

        mockMvc.perform(get("/api/v1/dashboards/99")).andExpect(status().isNotFound());
    }

    @Test
    void deleteDashboard_success_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboards/5")).andExpect(status().isNoContent());
        verify(dashboardService).deleteDashboard(USER_ID, 5L);
    }

    @Test
    void addWidget_invalidConfig_returnsBadRequest() throws Exception {
        when(dashboardService.addWidget(eq(USER_ID), eq(5L), any()))
                .thenThrow(new InvalidWidgetConfigException("config must be valid JSON"));

        mockMvc.perform(post("/api/v1/dashboards/5/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LOG_TABLE\",\"config\":\"not-json\","
                                + "\"positionX\":0,\"positionY\":0,\"width\":6,\"height\":8}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addWidget_invalidWidth_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/dashboards/5/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LOG_TABLE\",\"config\":\"{}\","
                                + "\"positionX\":0,\"positionY\":0,\"width\":99,\"height\":8}"))
                .andExpect(status().isBadRequest());
        verify(dashboardService, Mockito.never()).addWidget(any(), any(), any());
    }

    @Test
    void addWidget_unknownType_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/dashboards/5/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PIE_CHART\",\"config\":\"{}\","
                                + "\"positionX\":0,\"positionY\":0,\"width\":6,\"height\":8}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWidget_partialPayload_returnsOk() throws Exception {
        when(dashboardService.updateWidget(eq(USER_ID), eq(5L), eq(3L), any(UpdateWidgetRequest.class)))
                .thenReturn(sampleWidget());

        mockMvc.perform(put("/api/v1/dashboards/5/widgets/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionX\":4,\"positionY\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void deleteWidget_success_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboards/5/widgets/3")).andExpect(status().isNoContent());
        verify(dashboardService).deleteWidget(USER_ID, 5L, 3L);
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

        @ExceptionHandler({DashboardNotFoundException.class})
        ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", HttpStatus.NOT_FOUND.value(), "message", ex.getMessage()));
        }

        @ExceptionHandler(DashboardAccessDeniedException.class)
        ResponseEntity<Map<String, Object>> handleForbidden(DashboardAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", 403));
        }

        @ExceptionHandler(InvalidWidgetConfigException.class)
        ResponseEntity<Map<String, Object>> handleInvalidConfig(InvalidWidgetConfigException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", HttpStatus.BAD_REQUEST.value(), "message", ex.getMessage()));
        }
    }
}
