package com.monitoring.dashboards.controller;

import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.auth.security.PlatformAdminAccess;
import com.monitoring.dashboards.dto.DashboardVariable;
import com.monitoring.dashboards.dto.request.CreateDashboardRequest;
import com.monitoring.dashboards.dto.request.UpdateDashboardRequest;
import com.monitoring.dashboards.dto.response.DashboardResponse;
import com.monitoring.dashboards.dto.request.UpdateWidgetRequest;
import com.monitoring.dashboards.dto.response.WidgetResponse;
import com.monitoring.dashboards.entity.WidgetType;
import com.monitoring.dashboards.exception.DashboardAccessDeniedException;
import com.monitoring.dashboards.exception.DashboardNotFoundException;
import com.monitoring.dashboards.exception.InvalidWidgetConfigException;
import com.monitoring.dashboards.exception.SharedDashboardNotFoundException;
import com.monitoring.dashboards.service.DashboardService;
import com.monitoring.dashboards.service.SharedDashboardDataService;
import com.monitoring.collector.dto.response.AggregatedMetricResponse;
import com.monitoring.common.dto.response.PageResponse;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    private SharedDashboardDataService sharedDashboardDataService;
    private PlatformAdminAccess platformAdminAccess;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dashboardService = Mockito.mock(DashboardService.class);
        sharedDashboardDataService = Mockito.mock(SharedDashboardDataService.class);
        platformAdminAccess = Mockito.mock(PlatformAdminAccess.class);
        CurrentTenantService tenantService = Mockito.mock(CurrentTenantService.class);
        when(tenantService.requireOrganizationId()).thenReturn(USER_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(
                        dashboardService, sharedDashboardDataService, tenantService, platformAdminAccess))
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
                .organizationId(USER_ID)
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
                .andExpect(jsonPath("$[0].organizationId").value(USER_ID));
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
    void updateDashboard_returnsVariableDefinitions() throws Exception {
        DashboardResponse withVariables = DashboardResponse.builder()
                .id(5L)
                .name("Prod")
                .organizationId(USER_ID)
                .createdAt(Instant.parse("2026-10-09T10:00:00Z"))
                .variables(List.of(DashboardVariable.builder()
                        .name("env")
                        .type("custom")
                        .options(List.of("prod", "staging"))
                        .defaultValue("prod")
                        .build()))
                .widgets(List.of())
                .build();
        when(dashboardService.updateDashboard(eq(USER_ID), eq(5L), any(UpdateDashboardRequest.class), eq(false)))
                .thenReturn(withVariables);

        mockMvc.perform(put("/api/v1/dashboards/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Prod\",\"variables\":[{\"name\":\"env\",\"type\":\"custom\","
                                + "\"options\":[\"prod\",\"staging\"],\"defaultValue\":\"prod\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variables[0].name").value("env"))
                .andExpect(jsonPath("$.variables[0].options[1]").value("staging"));
    }

    @Test
    void updateDashboard_invalidVariableName_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/dashboards/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Prod\",\"variables\":[{\"name\":\"env name\",\"type\":\"custom\","
                                + "\"options\":[\"prod\"],\"defaultValue\":\"prod\"}]}"))
                .andExpect(status().isBadRequest());
        verify(dashboardService, Mockito.never()).updateDashboard(any(), any(), any(), anyBoolean());
    }

    @Test
    void updateDashboard_unknownVariableType_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/dashboards/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Prod\",\"variables\":[{\"name\":\"env\",\"type\":\"sql\","
                                + "\"options\":[\"prod\"],\"defaultValue\":\"prod\"}]}"))
                .andExpect(status().isBadRequest());
        verify(dashboardService, Mockito.never()).updateDashboard(any(), any(), any(), anyBoolean());
    }

    @Test
    void updateDashboard_variableWithoutOptions_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/dashboards/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Prod\",\"variables\":[{\"name\":\"env\",\"type\":\"custom\","
                                + "\"options\":[],\"defaultValue\":\"\"}]}"))
                .andExpect(status().isBadRequest());
        verify(dashboardService, Mockito.never()).updateDashboard(any(), any(), any(), anyBoolean());
    }

    @Test
    void getDashboard_anotherUser_returnsForbidden() throws Exception {
        when(dashboardService.getDashboard(USER_ID, 5L, false)).thenThrow(new DashboardAccessDeniedException());

        mockMvc.perform(get("/api/v1/dashboards/5")).andExpect(status().isForbidden());
    }

    @Test
    void getDashboard_missing_returnsNotFound() throws Exception {
        when(dashboardService.getDashboard(USER_ID, 99L, false)).thenThrow(new DashboardNotFoundException(99L));

        mockMvc.perform(get("/api/v1/dashboards/99")).andExpect(status().isNotFound());
    }

    @Test
    void deleteDashboard_success_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboards/5")).andExpect(status().isNoContent());
        verify(dashboardService).deleteDashboard(USER_ID, 5L, false);
    }

    @Test
    void addWidget_invalidConfig_returnsBadRequest() throws Exception {
        when(dashboardService.addWidget(eq(USER_ID), eq(5L), any(), eq(false)))
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
        verify(dashboardService, Mockito.never()).addWidget(any(), any(), any(), anyBoolean());
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
        when(dashboardService.updateWidget(eq(USER_ID), eq(5L), eq(3L), any(UpdateWidgetRequest.class), eq(false)))
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
        verify(dashboardService).deleteWidget(USER_ID, 5L, 3L, false);
    }

    @Test
    void generateShareToken_returnsDashboardWithToken() throws Exception {
        String token = UUID.randomUUID().toString();
        DashboardResponse sharedDashboard = DashboardResponse.builder()
                .id(5L)
                .name("Prod")
                .organizationId(USER_ID)
                .createdAt(Instant.parse("2026-10-09T10:00:00Z"))
                .widgets(List.of())
                .shareToken(token)
                .isPublic(true)
                .build();
        when(dashboardService.generateShareToken(USER_ID, 5L)).thenReturn(sharedDashboard);

        mockMvc.perform(post("/api/v1/dashboards/5/share"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareToken").value(token))
                .andExpect(jsonPath("$.isPublic").value(true));
        verify(dashboardService).generateShareToken(USER_ID, 5L);
    }

    @Test
    void revokeShareToken_returnsDashboardWithoutToken() throws Exception {
        DashboardResponse privateDashboard = DashboardResponse.builder()
                .id(5L)
                .name("Prod")
                .organizationId(USER_ID)
                .createdAt(Instant.parse("2026-10-09T10:00:00Z"))
                .widgets(List.of())
                .shareToken(null)
                .isPublic(false)
                .build();
        when(dashboardService.revokeShareToken(USER_ID, 5L)).thenReturn(privateDashboard);

        mockMvc.perform(delete("/api/v1/dashboards/5/share"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareToken").doesNotExist())
                .andExpect(jsonPath("$.isPublic").value(false));
        verify(dashboardService).revokeShareToken(USER_ID, 5L);
    }

    @Test
    void getDashboardByShareToken_validToken_returnsDashboard() throws Exception {
        String token = UUID.randomUUID().toString();
        DashboardResponse publicDashboard = DashboardResponse.builder()
                .id(5L)
                .name("Prod")
                .organizationId(null)
                .createdAt(Instant.parse("2026-10-09T10:00:00Z"))
                .widgets(List.of())
                .build();
        when(dashboardService.getDashboardByShareToken(token)).thenReturn(publicDashboard);

        mockMvc.perform(get("/api/v1/dashboards/share/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Prod"))
                .andExpect(jsonPath("$.organizationId").doesNotExist());
        verify(dashboardService).getDashboardByShareToken(token);
    }

    @Test
    void getDashboardByShareToken_invalidToken_returnsNotFound() throws Exception {
        when(dashboardService.getDashboardByShareToken("invalid-token")).thenReturn(null);

        mockMvc.perform(get("/api/v1/dashboards/share/invalid-token"))
                .andExpect(status().isNotFound());
        verify(dashboardService).getDashboardByShareToken("invalid-token");
    }

    @Test
    void getSharedWidgetMetrics_returnsSeries() throws Exception {
        String token = UUID.randomUUID().toString();
        Instant from = Instant.parse("2026-10-09T09:00:00Z");
        Instant to = Instant.parse("2026-10-09T10:00:00Z");
        when(sharedDashboardDataService.queryWidgetMetrics(token, 3L, from, to))
                .thenReturn(List.of(AggregatedMetricResponse.builder()
                        .timestamp(Instant.parse("2026-10-09T09:30:00Z"))
                        .value(42.5)
                        .build()));

        mockMvc.perform(get("/api/v1/dashboards/share/{token}/widgets/3/metrics", token)
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(42.5));
    }

    @Test
    void getSharedWidgetLogs_returnsPage() throws Exception {
        String token = UUID.randomUUID().toString();
        Instant from = Instant.parse("2026-10-09T09:00:00Z");
        Instant to = Instant.parse("2026-10-09T10:00:00Z");
        when(sharedDashboardDataService.queryWidgetLogs(token, 3L, from, to, 0, 10))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 0, 10));

        mockMvc.perform(get("/api/v1/dashboards/share/{token}/widgets/3/logs", token)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void getSharedWidgetMetrics_unknownToken_returnsNotFound() throws Exception {
        String token = UUID.randomUUID().toString();
        when(sharedDashboardDataService.queryWidgetMetrics(any(), any(), any(), any()))
                .thenThrow(new SharedDashboardNotFoundException());

        mockMvc.perform(get("/api/v1/dashboards/share/{token}/widgets/3/metrics", token)
                        .param("from", "2026-10-09T09:00:00Z")
                        .param("to", "2026-10-09T10:00:00Z"))
                .andExpect(status().isNotFound());
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

        @ExceptionHandler(SharedDashboardNotFoundException.class)
        ResponseEntity<Map<String, Object>> handleSharedNotFound(SharedDashboardNotFoundException ex) {
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
