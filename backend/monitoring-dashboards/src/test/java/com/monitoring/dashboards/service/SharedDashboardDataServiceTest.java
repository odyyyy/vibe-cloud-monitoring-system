package com.monitoring.dashboards.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.collector.dto.request.MetricAggregation;
import com.monitoring.collector.dto.request.MetricsQueryRequest;
import com.monitoring.collector.service.LogQueryService;
import com.monitoring.collector.service.MetricQueryService;
import com.monitoring.dashboards.entity.Dashboard;
import com.monitoring.dashboards.entity.Widget;
import com.monitoring.dashboards.entity.WidgetType;
import com.monitoring.dashboards.exception.InvalidSharedQueryException;
import com.monitoring.dashboards.exception.SharedDashboardNotFoundException;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.dashboards.repository.WidgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The public shared-dashboard data path must derive the queried application from the stored
 * widget configuration and must scope the query to the organization of the shared dashboard.
 */
class SharedDashboardDataServiceTest {

    private static final String TOKEN = "8bd1a0e6-0f9a-4a1a-9f77-9d5c0e1f2a3b";
    private static final Long ORGANIZATION_ID = 7L;
    private static final Long DASHBOARD_ID = 5L;
    private static final Long WIDGET_ID = 3L;

    private DashboardRepository dashboardRepository;
    private WidgetRepository widgetRepository;
    private MetricQueryService metricQueryService;
    private LogQueryService logQueryService;
    private SharedDashboardDataService service;

    @BeforeEach
    void setUp() {
        dashboardRepository = Mockito.mock(DashboardRepository.class);
        widgetRepository = Mockito.mock(WidgetRepository.class);
        metricQueryService = Mockito.mock(MetricQueryService.class);
        logQueryService = Mockito.mock(LogQueryService.class);
        service = new SharedDashboardDataService(
                dashboardRepository,
                widgetRepository,
                metricQueryService,
                logQueryService,
                new ObjectMapper());
    }

    private Dashboard sharedDashboard() {
        return Dashboard.builder()
                .id(DASHBOARD_ID)
                .name("Prod")
                .organizationId(ORGANIZATION_ID)
                .shareToken(TOKEN)
                .isPublic(true)
                .build();
    }

    private Widget widget(WidgetType type, String config) {
        return Widget.builder()
                .id(WIDGET_ID)
                .dashboard(sharedDashboard())
                .type(type)
                .config(config)
                .build();
    }

    @Test
    void queryWidgetMetrics_usesStoredConfigAndDashboardOrganization() {
        when(dashboardRepository.findByShareToken(TOKEN)).thenReturn(Optional.of(sharedDashboard()));
        when(widgetRepository.findByIdAndDashboard_Id(WIDGET_ID, DASHBOARD_ID)).thenReturn(Optional.of(
                widget(WidgetType.METRIC_CHART,
                        "{\"applicationId\":11,\"metricName\":\"cpu.usage\",\"aggregation\":\"MAX\",\"intervalMinutes\":5}")));

        Instant from = Instant.parse("2026-10-09T09:00:00Z");
        Instant to = Instant.parse("2026-10-09T10:00:00Z");
        service.queryWidgetMetrics(TOKEN, WIDGET_ID, from, to);

        ArgumentCaptor<MetricsQueryRequest> captor = ArgumentCaptor.forClass(MetricsQueryRequest.class);
        verify(metricQueryService).query(Mockito.eq(ORGANIZATION_ID), captor.capture());
        MetricsQueryRequest request = captor.getValue();
        assertEquals(11L, request.getApplicationId());
        assertEquals("cpu.usage", request.getMetricName());
        assertEquals(MetricAggregation.MAX, request.getAggregation());
        assertEquals(5, request.getIntervalMinutes());
    }

    @Test
    void queryWidgetMetrics_revokedDashboard_returnsNotFound() {
        when(dashboardRepository.findByShareToken(TOKEN)).thenReturn(Optional.empty());

        assertThrows(SharedDashboardNotFoundException.class, () -> service.queryWidgetMetrics(
                TOKEN, WIDGET_ID, Instant.parse("2026-10-09T09:00:00Z"), Instant.parse("2026-10-09T10:00:00Z")));
    }

    @Test
    void queryWidgetMetrics_widgetOfAnotherDashboard_returnsNotFound() {
        when(dashboardRepository.findByShareToken(TOKEN)).thenReturn(Optional.of(sharedDashboard()));
        when(widgetRepository.findByIdAndDashboard_Id(WIDGET_ID, DASHBOARD_ID)).thenReturn(Optional.empty());

        assertThrows(SharedDashboardNotFoundException.class, () -> service.queryWidgetMetrics(
                TOKEN, WIDGET_ID, Instant.parse("2026-10-09T09:00:00Z"), Instant.parse("2026-10-09T10:00:00Z")));
    }

    @Test
    void queryWidgetMetrics_rangeTooLong_returnsBadRequest() {
        when(dashboardRepository.findByShareToken(TOKEN)).thenReturn(Optional.of(sharedDashboard()));
        when(widgetRepository.findByIdAndDashboard_Id(WIDGET_ID, DASHBOARD_ID)).thenReturn(Optional.of(
                widget(WidgetType.METRIC_CHART, "{\"applicationId\":11,\"metricName\":\"cpu.usage\"}")));

        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-09T10:00:00Z");
        assertThrows(InvalidSharedQueryException.class, () -> service.queryWidgetMetrics(TOKEN, WIDGET_ID, from, to));
    }

    @Test
    void queryWidgetLogs_usesStoredConfigAndDashboardOrganization() {
        when(dashboardRepository.findByShareToken(TOKEN)).thenReturn(Optional.of(sharedDashboard()));
        when(widgetRepository.findByIdAndDashboard_Id(WIDGET_ID, DASHBOARD_ID)).thenReturn(Optional.of(
                widget(WidgetType.LOG_TABLE,
                        "{\"applicationId\":11,\"level\":\"ERROR\",\"searchText\":\"boom\",\"timeRangeHours\":1}")));

        Instant from = Instant.parse("2026-10-09T09:00:00Z");
        Instant to = Instant.parse("2026-10-09T10:00:00Z");
        service.queryWidgetLogs(TOKEN, WIDGET_ID, from, to, 0, 10);

        ArgumentCaptor<LogsQueryRequest> captor = ArgumentCaptor.forClass(LogsQueryRequest.class);
        verify(logQueryService).query(Mockito.eq(ORGANIZATION_ID), captor.capture());
        LogsQueryRequest request = captor.getValue();
        assertEquals(11L, request.getApplicationId());
        assertEquals("boom", request.getSearchText());
        assertEquals(10, request.getSize());
    }

    @Test
    void queryWidgetLogs_metricChartWidget_returnsBadRequest() {
        when(dashboardRepository.findByShareToken(TOKEN)).thenReturn(Optional.of(sharedDashboard()));
        when(widgetRepository.findByIdAndDashboard_Id(WIDGET_ID, DASHBOARD_ID)).thenReturn(Optional.of(
                widget(WidgetType.METRIC_CHART, "{\"applicationId\":11,\"metricName\":\"cpu.usage\"}")));

        Instant from = Instant.parse("2026-10-09T09:00:00Z");
        Instant to = Instant.parse("2026-10-09T10:00:00Z");
        assertThrows(InvalidSharedQueryException.class,
                () -> service.queryWidgetLogs(TOKEN, WIDGET_ID, from, to, 0, 10));
        verify(logQueryService, Mockito.never()).query(any(), any());
    }
}
