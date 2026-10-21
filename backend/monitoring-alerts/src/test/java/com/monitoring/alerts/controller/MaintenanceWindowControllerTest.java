package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.request.CreateMaintenanceWindowRequest;
import com.monitoring.alerts.dto.request.UpdateMaintenanceWindowRequest;
import com.monitoring.alerts.dto.response.MaintenanceWindowResponse;
import com.monitoring.alerts.entity.MaintenanceScopeType;
import com.monitoring.alerts.exception.InvalidMaintenanceWindowException;
import com.monitoring.alerts.exception.MaintenanceWindowAccessDeniedException;
import com.monitoring.alerts.exception.MaintenanceWindowNotFoundException;
import com.monitoring.alerts.service.MaintenanceWindowService;
import com.monitoring.organization.tenant.CurrentTenantService;
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

class MaintenanceWindowControllerTest {

    private static final Long ORG_ID = 42L;

    private MaintenanceWindowService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(MaintenanceWindowService.class);
        CurrentTenantService tenantService = Mockito.mock(CurrentTenantService.class);
        when(tenantService.requireOrganizationId()).thenReturn(ORG_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MaintenanceWindowController(service, tenantService))
                .setControllerAdvice(new TestAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ORG_ID.toString(), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MaintenanceWindowResponse sampleWindow() {
        return MaintenanceWindowResponse.builder()
                .id(5L)
                .organizationId(ORG_ID)
                .name("Deploy")
                .scopeType(MaintenanceScopeType.APPLICATION)
                .scopeId(7L)
                .startTime(Instant.parse("2026-10-21T10:00:00Z"))
                .endTime(Instant.parse("2026-10-21T12:00:00Z"))
                .active(true)
                .build();
    }

    @Test
    void list_returnsTenantWindows() throws Exception {
        when(service.list(ORG_ID)).thenReturn(List.of(sampleWindow()));

        mockMvc.perform(get("/api/v1/maintenance-windows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].scopeType").value("APPLICATION"))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    void create_valid_returnsCreatedWithLocation() throws Exception {
        when(service.create(eq(ORG_ID), any(CreateMaintenanceWindowRequest.class)))
                .thenReturn(sampleWindow());

        mockMvc.perform(post("/api/v1/maintenance-windows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Deploy\",\"scopeType\":\"APPLICATION\",\"scopeId\":7,"
                                + "\"startTime\":\"2026-10-21T10:00:00Z\","
                                + "\"endTime\":\"2026-10-21T12:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/maintenance-windows/5"));
    }

    @Test
    void create_blankName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/maintenance-windows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"scopeType\":\"GLOBAL\","
                                + "\"startTime\":\"2026-10-21T10:00:00Z\","
                                + "\"endTime\":\"2026-10-21T12:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        verify(service, Mockito.never()).create(any(), any());
    }

    @Test
    void create_missingTimes_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/maintenance-windows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Deploy\",\"scopeType\":\"GLOBAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startTime").exists())
                .andExpect(jsonPath("$.fieldErrors.endTime").exists());
    }

    @Test
    void create_invalidScope_returnsBadRequest() throws Exception {
        when(service.create(eq(ORG_ID), any(CreateMaintenanceWindowRequest.class)))
                .thenThrow(new InvalidMaintenanceWindowException("scopeId is required"));

        mockMvc.perform(post("/api/v1/maintenance-windows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Deploy\",\"scopeType\":\"APPLICATION\","
                                + "\"startTime\":\"2026-10-21T10:00:00Z\","
                                + "\"endTime\":\"2026-10-21T12:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_foreignWindow_returnsForbidden() throws Exception {
        when(service.update(eq(ORG_ID), eq(5L), any(UpdateMaintenanceWindowRequest.class)))
                .thenThrow(new MaintenanceWindowAccessDeniedException());

        mockMvc.perform(put("/api/v1/maintenance-windows/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_missingWindow_returnsNotFound() throws Exception {
        when(service.update(eq(ORG_ID), eq(99L), any(UpdateMaintenanceWindowRequest.class)))
                .thenThrow(new MaintenanceWindowNotFoundException(99L));

        mockMvc.perform(put("/api/v1/maintenance-windows/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_success_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/maintenance-windows/5"))
                .andExpect(status().isNoContent());
        verify(service).delete(ORG_ID, 5L);
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

        @ExceptionHandler(MaintenanceWindowNotFoundException.class)
        ResponseEntity<Map<String, Object>> handleNotFound(MaintenanceWindowNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", HttpStatus.NOT_FOUND.value(), "message", ex.getMessage()));
        }

        @ExceptionHandler(MaintenanceWindowAccessDeniedException.class)
        ResponseEntity<Map<String, Object>> handleForbidden(MaintenanceWindowAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", 403));
        }

        @ExceptionHandler(InvalidMaintenanceWindowException.class)
        ResponseEntity<Map<String, Object>> handleInvalid(InvalidMaintenanceWindowException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", HttpStatus.BAD_REQUEST.value(), "message", ex.getMessage()));
        }
    }
}
