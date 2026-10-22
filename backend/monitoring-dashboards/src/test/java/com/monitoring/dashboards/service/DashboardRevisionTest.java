package com.monitoring.dashboards.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.dashboards.dto.DashboardVariable;
import com.monitoring.dashboards.dto.request.CreateDashboardRequest;
import com.monitoring.dashboards.dto.request.UpdateDashboardRequest;
import com.monitoring.dashboards.dto.response.DashboardResponse;
import com.monitoring.dashboards.dto.snapshot.DashboardSnapshot;
import com.monitoring.dashboards.entity.Dashboard;
import com.monitoring.dashboards.entity.Widget;
import com.monitoring.dashboards.entity.WidgetType;
import com.monitoring.dashboards.exception.DashboardAccessDeniedException;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.dashboards.repository.WidgetRepository;
import com.monitoring.revisions.entity.ConfigChangeType;
import com.monitoring.revisions.entity.ConfigResourceType;
import com.monitoring.revisions.entity.ConfigRevision;
import com.monitoring.revisions.exception.ConfigRevisionRestoreConflictException;
import com.monitoring.revisions.exception.InvalidConfigSnapshotException;
import com.monitoring.revisions.service.ConfigRevisionService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardRevisionTest {

    private static final Long ORG_ID = 1L;
    private static final Long DASHBOARD_ID = 5L;
    private static final Long APPLICATION_ID = 7L;

    private DashboardRepository dashboardRepository;
    private WidgetRepository widgetRepository;
    private ApplicationService applicationService;
    private ConfigRevisionService revisionService;
    private DashboardService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        dashboardRepository = Mockito.mock(DashboardRepository.class);
        widgetRepository = Mockito.mock(WidgetRepository.class);
        applicationService = Mockito.mock(ApplicationService.class);
        revisionService = Mockito.mock(ConfigRevisionService.class);
        objectMapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new DashboardService(
                dashboardRepository, widgetRepository, objectMapper, applicationService, revisionService, validator);

        when(dashboardRepository.save(any(Dashboard.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dashboardRepository.saveAndFlush(any(Dashboard.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createDashboardRecordsTheInitialSnapshot() {
        when(dashboardRepository.save(any(Dashboard.class))).thenAnswer(invocation -> {
            Dashboard saved = invocation.getArgument(0);
            saved.setId(DASHBOARD_ID);
            return saved;
        });
        when(widgetRepository.findByDashboard_IdOrderByPositionYAscPositionXAscIdAsc(DASHBOARD_ID))
                .thenReturn(List.of());

        service.createDashboard(ORG_ID, new CreateDashboardRequest("Prod"));

        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(revisionService).recordCreate(eq(ORG_ID), eq(ConfigResourceType.DASHBOARD),
                eq(DASHBOARD_ID), eq(DashboardService.SNAPSHOT_SCHEMA_VERSION), snapshot.capture());
        assertTrue(snapshot.getValue().contains("\"name\":\"Prod\""));
    }

    @Test
    void updateDashboardPassesThePreAndPostChangeSnapshots() {
        Dashboard dashboard = dashboard(DASHBOARD_ID, "Old", false);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(widgetRepository.findByDashboard_IdOrderByPositionYAscPositionXAscIdAsc(DASHBOARD_ID))
                .thenReturn(List.of());

        service.updateDashboard(ORG_ID, DASHBOARD_ID, new UpdateDashboardRequest("New", List.of()));

        verify(revisionService).recordUpdate(
                eq(ORG_ID), eq(ConfigResourceType.DASHBOARD), eq(DASHBOARD_ID),
                eq(DashboardService.SNAPSHOT_SCHEMA_VERSION),
                argThat(value -> value.contains("\"name\":\"Old\"")),
                argThat(value -> value.contains("\"name\":\"New\"")));
    }

    @Test
    void updatingASystemDashboardDoesNotCreateHistory() {
        Dashboard system = dashboard(DASHBOARD_ID, "Platform", true);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(system));

        service.updateDashboard(ORG_ID, DASHBOARD_ID, new UpdateDashboardRequest("Platform 2", null), true);

        verify(revisionService, never()).recordUpdate(anyLong(), any(), anyLong(), anyInt(), any(), any());
        verify(revisionService, never()).recordCreate(anyLong(), any(), anyLong(), anyInt(), any());
    }

    @Test
    void restoringARevisionAppliesTheSnapshotAndRecordsARestore() throws Exception {
        Dashboard dashboard = dashboard(DASHBOARD_ID, "Current", false);
        dashboard.getWidgets().add(widget(dashboard, "current"));
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(widgetRepository.findByDashboard_IdOrderByPositionYAscPositionXAscIdAsc(DASHBOARD_ID))
                .thenAnswer(invocation -> new ArrayList<>(dashboard.getWidgets()));
        when(applicationService.getOwnedApplication(APPLICATION_ID, ORG_ID)).thenReturn(new Application());

        DashboardSnapshot snapshot = new DashboardSnapshot(
                "Restored",
                List.of(new DashboardVariable("env", "custom", List.of("prod"), "prod")),
                List.of(new DashboardSnapshot.WidgetSnapshot(
                        WidgetType.METRIC_CHART,
                        "{\"applicationId\":7,\"metricName\":\"cpu\"}",
                        0, 0, 6, 4)));
        when(revisionService.requireRevision(ORG_ID, ConfigResourceType.DASHBOARD, DASHBOARD_ID, 2))
                .thenReturn(revision(2, snapshot));

        DashboardResponse response = service.restoreRevision(ORG_ID, DASHBOARD_ID, 2);

        assertEquals("Restored", response.getName());
        assertEquals(1, response.getWidgets().size());
        assertEquals(WidgetType.METRIC_CHART, dashboard.getWidgets().get(0).getType());
        verify(revisionService).recordRestore(eq(ORG_ID), eq(ConfigResourceType.DASHBOARD),
                eq(DASHBOARD_ID), eq(2), eq(DashboardService.SNAPSHOT_SCHEMA_VERSION), any());
    }

    @Test
    void restoringARevisionWithADeletedApplicationChangesNothing() {
        Dashboard dashboard = dashboard(DASHBOARD_ID, "Current", false);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(applicationService.getOwnedApplication(APPLICATION_ID, ORG_ID))
                .thenThrow(new RuntimeException("missing"));
        when(revisionService.requireRevision(ORG_ID, ConfigResourceType.DASHBOARD, DASHBOARD_ID, 1))
                .thenReturn(revision(1, new DashboardSnapshot(
                        "Restored", List.of(),
                        List.of(new DashboardSnapshot.WidgetSnapshot(
                                WidgetType.METRIC_CHART,
                                "{\"applicationId\":7}",
                                0, 0, 6, 4)))));

        assertThrows(ConfigRevisionRestoreConflictException.class,
                () -> service.restoreRevision(ORG_ID, DASHBOARD_ID, 1));
        assertEquals("Current", dashboard.getName());
        verify(revisionService, never()).recordRestore(anyLong(), any(), anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void restoringAnUnknownSnapshotFieldIsRejected() {
        Dashboard dashboard = dashboard(DASHBOARD_ID, "Current", false);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(revisionService.requireRevision(ORG_ID, ConfigResourceType.DASHBOARD, DASHBOARD_ID, 1))
                .thenReturn(ConfigRevision.builder()
                        .organizationId(ORG_ID)
                        .resourceType(ConfigResourceType.DASHBOARD)
                        .resourceId(DASHBOARD_ID)
                        .revisionNumber(1)
                        .changeType(ConfigChangeType.CREATE)
                        .schemaVersion(DashboardService.SNAPSHOT_SCHEMA_VERSION)
                        .snapshot("{\"name\":\"x\",\"variables\":[],\"widgets\":[],\"shareToken\":\"leak\"}")
                        .build());

        assertThrows(InvalidConfigSnapshotException.class,
                () -> service.restoreRevision(ORG_ID, DASHBOARD_ID, 1));
        assertEquals("Current", dashboard.getName());
    }

    @Test
    void foreignDashboardHistoryIsNotReachable() {
        when(dashboardRepository.findById(DASHBOARD_ID))
                .thenReturn(Optional.of(dashboard(DASHBOARD_ID, "Other tenant", false)));

        assertThrows(DashboardAccessDeniedException.class,
                () -> service.listRevisions(2L, DASHBOARD_ID, 0, 10));
        verify(revisionService, never()).list(anyLong(), any(), anyLong(), any(), any());
    }

    private Dashboard dashboard(Long id, String name, boolean system) {
        return Dashboard.builder()
                .id(id)
                .name(name)
                .organizationId(ORG_ID)
                .isSystem(system)
                .build();
    }

    private Widget widget(Dashboard dashboard, String name) {
        return Widget.builder()
                .dashboard(dashboard)
                .type(WidgetType.STATUS)
                .config("{\"applicationId\":7,\"name\":\"" + name + "\"}")
                .positionX(0)
                .positionY(0)
                .width(4)
                .height(3)
                .build();
    }

    private ConfigRevision revision(int number, DashboardSnapshot snapshot) {
        try {
            return ConfigRevision.builder()
                    .organizationId(ORG_ID)
                    .resourceType(ConfigResourceType.DASHBOARD)
                    .resourceId(DASHBOARD_ID)
                    .revisionNumber(number)
                    .changeType(ConfigChangeType.UPDATE)
                    .schemaVersion(DashboardService.SNAPSHOT_SCHEMA_VERSION)
                    .snapshot(objectMapper.writeValueAsString(snapshot))
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
