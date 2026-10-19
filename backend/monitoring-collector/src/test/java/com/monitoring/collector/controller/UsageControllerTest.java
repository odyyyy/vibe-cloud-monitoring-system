package com.monitoring.collector.controller;

import com.monitoring.collector.dto.response.CurrentMonthUsageResponse;
import com.monitoring.collector.dto.response.DailyUsagePointResponse;
import com.monitoring.collector.dto.response.UsageMeterResponse;
import com.monitoring.collector.service.UsageQueryService;
import com.monitoring.organization.exception.TenantAccessDeniedException;
import com.monitoring.organization.tenant.CurrentTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsageControllerTest {

    private static final Long ORGANIZATION_ID = 42L;

    private UsageQueryService usageQueryService;
    private CurrentTenantService tenantService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        usageQueryService = Mockito.mock(UsageQueryService.class);
        tenantService = Mockito.mock(CurrentTenantService.class);
        when(tenantService.requireOrganizationId()).thenReturn(ORGANIZATION_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UsageController(usageQueryService, tenantService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build()))
                .setControllerAdvice(new TestAdvice())
                .build();
    }

    @Test
    void currentMonth_returnsMetersScopedToActiveOrganization() throws Exception {
        when(usageQueryService.currentMonth(eq(ORGANIZATION_ID), any())).thenReturn(
                CurrentMonthUsageResponse.builder()
                        .organizationId(ORGANIZATION_ID)
                        .periodStart(LocalDate.of(2026, 10, 1))
                        .periodEnd(LocalDate.of(2026, 10, 31))
                        .metrics(UsageMeterResponse.builder().used(900).limit(1000).percentage(90.0).build())
                        .logs(UsageMeterResponse.builder().used(10).limit(500).percentage(2.0).build())
                        .traces(UsageMeterResponse.builder().used(0).limit(100).percentage(0.0).build())
                        .build());

        mockMvc.perform(get("/api/v1/usage/current-month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(ORGANIZATION_ID))
                .andExpect(jsonPath("$.metrics.used").value(900))
                .andExpect(jsonPath("$.metrics.percentage").value(90.0))
                .andExpect(jsonPath("$.logs.limit").value(500));

        verify(usageQueryService).currentMonth(eq(ORGANIZATION_ID), any());
    }

    @Test
    void daily_defaultsToThirtyDays() throws Exception {
        when(usageQueryService.daily(eq(ORGANIZATION_ID), any(), eq(30))).thenReturn(
                List.of(DailyUsagePointResponse.builder()
                        .date(LocalDate.of(2026, 10, 10))
                        .metrics(1).logs(2).traces(3).build()));

        mockMvc.perform(get("/api/v1/usage/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].traces").value(3));

        verify(usageQueryService).daily(eq(ORGANIZATION_ID), any(), eq(30));
    }

    @Test
    void daily_passesRequestedWindow() throws Exception {
        when(usageQueryService.daily(eq(ORGANIZATION_ID), any(), eq(7))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/usage/daily").param("days", "7"))
                .andExpect(status().isOk());

        verify(usageQueryService).daily(eq(ORGANIZATION_ID), any(), eq(7));
    }

    @Test
    void currentMonth_foreignOrganization_returnsForbidden() throws Exception {
        when(tenantService.requireOrganizationId()).thenThrow(new TenantAccessDeniedException());

        mockMvc.perform(get("/api/v1/usage/current-month").header("X-Organization-Id", "999"))
                .andExpect(status().isForbidden());

        verify(usageQueryService, never()).currentMonth(any(), any());
    }

    /** Maps the same exceptions the global ApiExceptionHandler handles in production. */
    @RestControllerAdvice
    static class TestAdvice {

        @ExceptionHandler(TenantAccessDeniedException.class)
        ResponseEntity<Map<String, Object>> handleForbidden(TenantAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", HttpStatus.FORBIDDEN.value(), "message", ex.getMessage()));
        }
    }
}
