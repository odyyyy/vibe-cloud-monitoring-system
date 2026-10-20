package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.request.UpdateIncidentNotesRequest;
import com.monitoring.alerts.dto.response.AlertIncidentResponse;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.service.AlertService;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.organization.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertIncidentControllerTest {

    private static final Long ORGANIZATION_ID = 7L;
    private static final Long USER_ID = 42L;

    private AlertService alertService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        alertService = Mockito.mock(AlertService.class);
        CurrentTenantService tenantService = Mockito.mock(CurrentTenantService.class);
        when(tenantService.requireOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(tenantService.requireTenant())
                .thenReturn(new TenantContext(USER_ID, ORGANIZATION_ID, MembershipRole.ADMIN));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AlertIncidentController(alertService, tenantService))
                .build();
    }

    private AlertIncidentResponse incident(IncidentStatus status) {
        return AlertIncidentResponse.builder()
                .id(3L)
                .ruleId(9L)
                .ruleName("High CPU")
                .status(status)
                .triggeredAt(Instant.parse("2026-10-17T10:00:00Z"))
                .acknowledgedBy(status == IncidentStatus.ACKNOWLEDGED ? USER_ID : null)
                .acknowledgedByName(status == IncidentStatus.ACKNOWLEDGED ? "oncall" : null)
                .acknowledgedAt(status == IncidentStatus.ACKNOWLEDGED ? Instant.parse("2026-10-17T10:01:00Z") : null)
                .build();
    }

    @Test
    void listReturnsIncidentsAndPassesStatusFilter() throws Exception {
        when(alertService.listIncidents(eq(ORGANIZATION_ID), isNull(), eq(IncidentStatus.ACKNOWLEDGED)))
                .thenReturn(List.of(incident(IncidentStatus.ACKNOWLEDGED)));

        mockMvc.perform(get("/api/v1/incidents").param("status", "ACKNOWLEDGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$[0].acknowledgedByName").value("oncall"));

        verify(alertService).listIncidents(ORGANIZATION_ID, null, IncidentStatus.ACKNOWLEDGED);
    }

    @Test
    void acknowledgeUsesActiveTenantAndUser() throws Exception {
        when(alertService.acknowledgeIncident(ORGANIZATION_ID, USER_ID, 3L))
                .thenReturn(incident(IncidentStatus.ACKNOWLEDGED));

        mockMvc.perform(post("/api/v1/incidents/3/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedByName").value("oncall"));
    }

    @Test
    void resolveUsesActiveTenantAndUser() throws Exception {
        when(alertService.resolveIncident(ORGANIZATION_ID, USER_ID, 3L))
                .thenReturn(incident(IncidentStatus.RESOLVED));

        mockMvc.perform(post("/api/v1/incidents/3/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void notesAreSavedThroughTheApi() throws Exception {
        AlertIncidentResponse withNotes = AlertIncidentResponse.builder()
                .id(3L)
                .ruleId(9L)
                .ruleName("High CPU")
                .status(IncidentStatus.FIRING)
                .triggeredAt(Instant.parse("2026-10-17T10:00:00Z"))
                .notes("Root cause: cache stampede.")
                .build();
        when(alertService.updateIncidentNotes(eq(ORGANIZATION_ID), eq(3L), Mockito.any()))
                .thenReturn(withNotes);

        mockMvc.perform(put("/api/v1/incidents/3/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Root cause: cache stampede.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Root cause: cache stampede."));

        verify(alertService).updateIncidentNotes(eq(ORGANIZATION_ID), eq(3L),
                Mockito.argThat(request -> request instanceof UpdateIncidentNotesRequest));
    }

    @Test
    void legacyPathStillListsIncidents() throws Exception {
        when(alertService.listIncidents(eq(ORGANIZATION_ID), eq(10), isNull()))
                .thenReturn(List.of(incident(IncidentStatus.FIRING)));

        mockMvc.perform(get("/api/v1/alert-incidents").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FIRING"));
    }
}
