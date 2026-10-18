package com.monitoring.api;

import com.monitoring.dashboards.dto.request.CreateDashboardRequest;
import com.monitoring.dashboards.dto.request.CreateWidgetRequest;
import com.monitoring.dashboards.dto.response.DashboardResponse;
import com.monitoring.dashboards.dto.request.UpdateDashboardRequest;
import com.monitoring.dashboards.dto.request.UpdateWidgetRequest;
import com.monitoring.dashboards.dto.response.WidgetResponse;
import com.monitoring.dashboards.entity.WidgetType;
import com.monitoring.dashboards.exception.DashboardAccessDeniedException;
import com.monitoring.dashboards.exception.InvalidWidgetConfigException;
import com.monitoring.dashboards.exception.WidgetLimitExceededException;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.dashboards.repository.WidgetRepository;
import com.monitoring.dashboards.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies dashboard/widget persistence, tenant isolation, layout ordering and widget config
 * validation against a real persistence context.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dashboards-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class DashboardsIntegrationTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private WidgetRepository widgetRepository;

    @BeforeEach
    void setUp() {
        widgetRepository.deleteAll();
        dashboardRepository.deleteAll();
    }

    private CreateWidgetRequest widget(WidgetType type, int x, int y) {
        return CreateWidgetRequest.builder()
                .type(type)
                .config("{\"applicationId\":1}")
                .positionX(x)
                .positionY(y)
                .width(6)
                .height(8)
                .build();
    }

    @Test
    void createListAndGetDashboard() {
        DashboardResponse created = dashboardService.createDashboard(
                OWNER_ID, CreateDashboardRequest.builder().name("Prod").build());

        List<DashboardResponse> list = dashboardService.listDashboards(OWNER_ID);
        assertEquals(1, list.size());
        assertEquals("Prod", list.get(0).getName());
        assertEquals(OWNER_ID, list.get(0).getOrganizationId());

        DashboardResponse fetched = dashboardService.getDashboard(OWNER_ID, created.getId());
        assertEquals(created.getId(), fetched.getId());
        assertTrue(fetched.getWidgets().isEmpty());
    }

    @Test
    void renameAndDeleteDashboard() {
        DashboardResponse created = dashboardService.createDashboard(
                OWNER_ID, CreateDashboardRequest.builder().name("Prod").build());

        DashboardResponse renamed = dashboardService.updateDashboard(
                OWNER_ID, created.getId(), UpdateDashboardRequest.builder().name("Staging").build());
        assertEquals("Staging", renamed.getName());

        dashboardService.deleteDashboard(OWNER_ID, created.getId());
        assertEquals(0, dashboardRepository.count());
    }

    @Test
    void widgetsAreOrderedByLayout() {
        DashboardResponse dashboard = dashboardService.createDashboard(
                OWNER_ID, CreateDashboardRequest.builder().name("Prod").build());
        dashboardService.addWidget(OWNER_ID, dashboard.getId(), widget(WidgetType.LOG_TABLE, 0, 5));
        dashboardService.addWidget(OWNER_ID, dashboard.getId(), widget(WidgetType.METRIC_CHART, 0, 0));

        DashboardResponse fetched = dashboardService.getDashboard(OWNER_ID, dashboard.getId());
        assertEquals(List.of(WidgetType.METRIC_CHART, WidgetType.LOG_TABLE),
                fetched.getWidgets().stream().map(WidgetResponse::getType).toList());
    }

    @Test
    void updateWidgetAppliesPartialFields() {
        DashboardResponse dashboard = dashboardService.createDashboard(
                OWNER_ID, CreateDashboardRequest.builder().name("Prod").build());
        WidgetResponse created = dashboardService.addWidget(
                OWNER_ID, dashboard.getId(), widget(WidgetType.METRIC_CHART, 0, 0));

        WidgetResponse updated = dashboardService.updateWidget(OWNER_ID, dashboard.getId(), created.getId(),
                UpdateWidgetRequest.builder().positionX(4).positionY(2).build());

        assertEquals(4, updated.getPositionX());
        assertEquals(2, updated.getPositionY());
        assertEquals(WidgetType.METRIC_CHART, updated.getType());
    }

    @Test
    void deleteWidgetRemovesOnlyThatWidget() {
        DashboardResponse dashboard = dashboardService.createDashboard(
                OWNER_ID, CreateDashboardRequest.builder().name("Prod").build());
        WidgetResponse first = dashboardService.addWidget(OWNER_ID, dashboard.getId(), widget(WidgetType.STATUS, 0, 0));
        dashboardService.addWidget(OWNER_ID, dashboard.getId(), widget(WidgetType.LOG_TABLE, 0, 5));

        dashboardService.deleteWidget(OWNER_ID, dashboard.getId(), first.getId());

        assertEquals(1, widgetRepository.countByDashboard_Id(dashboard.getId()));
    }

    @Test
    void invalidWidgetConfigIsRejected() {
        DashboardResponse dashboard = dashboardService.createDashboard(
                OWNER_ID, CreateDashboardRequest.builder().name("Prod").build());
        CreateWidgetRequest bad = CreateWidgetRequest.builder()
                .type(WidgetType.LOG_TABLE)
                .config("not-json")
                .width(6)
                .height(8)
                .build();

        assertThrows(InvalidWidgetConfigException.class,
                () -> dashboardService.addWidget(OWNER_ID, dashboard.getId(), bad));
    }

    @Test
    void widgetLimitIsEnforced() {
        DashboardResponse dashboard = dashboardService.createDashboard(
                OWNER_ID, CreateDashboardRequest.builder().name("Prod").build());
        IntFunction<CreateWidgetRequest> widgetAt = i -> widget(WidgetType.STATUS, i % 12, i / 12);

        for (int i = 0; i < DashboardService.MAX_WIDGETS_PER_DASHBOARD; i++) {
            dashboardService.addWidget(OWNER_ID, dashboard.getId(), widgetAt.apply(i));
        }

        assertThrows(WidgetLimitExceededException.class,
                () -> dashboardService.addWidget(OWNER_ID, dashboard.getId(), widgetAt.apply(99)));
    }

    @Test
    void anotherUserCannotAccessDashboard() {
        DashboardResponse dashboard = dashboardService.createDashboard(
                OWNER_ID, CreateDashboardRequest.builder().name("Prod").build());

        assertEquals(0, dashboardService.listDashboards(OTHER_ID).size());
        assertThrows(DashboardAccessDeniedException.class,
                () -> dashboardService.getDashboard(OTHER_ID, dashboard.getId()));
        assertThrows(DashboardAccessDeniedException.class,
                () -> dashboardService.deleteDashboard(OTHER_ID, dashboard.getId()));
    }

    @Test
    void deletingDashboardCascadesToWidgets() {
        DashboardResponse dashboard = dashboardService.createDashboard(
                OWNER_ID, CreateDashboardRequest.builder().name("Prod").build());
        dashboardService.addWidget(OWNER_ID, dashboard.getId(), widget(WidgetType.STATUS, 0, 0));

        dashboardService.deleteDashboard(OWNER_ID, dashboard.getId());

        assertEquals(0, widgetRepository.count());
    }
}
