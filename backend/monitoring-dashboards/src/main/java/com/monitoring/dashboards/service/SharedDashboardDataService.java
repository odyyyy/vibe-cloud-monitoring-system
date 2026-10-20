package com.monitoring.dashboards.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.collector.dto.request.MetricAggregation;
import com.monitoring.collector.dto.request.MetricsQueryRequest;
import com.monitoring.collector.dto.response.AggregatedMetricResponse;
import com.monitoring.collector.dto.response.LogEventResponse;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.service.LogQueryService;
import com.monitoring.collector.service.MetricQueryService;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.dashboards.entity.Dashboard;
import com.monitoring.dashboards.entity.Widget;
import com.monitoring.dashboards.entity.WidgetType;
import com.monitoring.dashboards.exception.InvalidSharedQueryException;
import com.monitoring.dashboards.exception.SharedDashboardNotFoundException;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.dashboards.repository.WidgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Serves the telemetry behind a publicly shared dashboard.
 *
 * <p>A public link is authorized by the unguessable share token only. The dashboard behind
 * the token determines the tenant: the widget configuration is read from the database
 * (never from the request), so a caller cannot point a shared dashboard at another
 * tenant's application. The organization of the dashboard is passed to the query services,
 * which re-check that the configured application belongs to it.
 */
@Service
@RequiredArgsConstructor
public class SharedDashboardDataService {

    /** Maximum number of log rows returned for one public widget request. */
    public static final int MAX_LOG_PAGE_SIZE = 100;

    /** Default number of log rows returned for one public widget request. */
    public static final int DEFAULT_LOG_PAGE_SIZE = 25;

    /** Maximum queried range in days; protects the database from heavy scans. */
    public static final long MAX_RANGE_DAYS = 30;

    private static final int DEFAULT_INTERVAL_MINUTES = 5;

    private final DashboardRepository dashboardRepository;
    private final WidgetRepository widgetRepository;
    private final MetricQueryService metricQueryService;
    private final LogQueryService logQueryService;
    private final ObjectMapper objectMapper;

    /**
     * Returns the aggregated metric series of a METRIC_CHART widget of a shared dashboard.
     *
     * <p>The application, metric name, aggregation and interval come from the stored widget
     * configuration; the caller only selects the time window.
     */
    @Transactional(readOnly = true)
    public List<AggregatedMetricResponse> queryWidgetMetrics(String token, Long widgetId, Instant from, Instant to) {
        Dashboard dashboard = requireSharedDashboard(token);
        Widget widget = requireWidget(dashboard, widgetId);
        if (widget.getType() != WidgetType.METRIC_CHART) {
            throw new InvalidSharedQueryException("Widget " + widgetId + " is not a metric chart");
        }

        JsonNode config = readConfig(widget);
        String metricName = readText(config, "metricName");
        if (metricName == null) {
            throw new InvalidSharedQueryException("Widget configuration has no valid metricName");
        }
        MetricsQueryRequest request = MetricsQueryRequest.builder()
                .applicationId(readApplicationId(config))
                .metricName(metricName)
                .from(from)
                .to(to)
                .aggregation(readAggregation(config))
                .intervalMinutes(readIntervalMinutes(config))
                .build();

        if (!request.isTimeRangeValid()) {
            throw new InvalidSharedQueryException(
                    "The requested time range is invalid or longer than " + MAX_RANGE_DAYS + " days");
        }

        return metricQueryService.query(dashboard.getOrganizationId(), request);
    }

    /**
     * Returns the log rows of a LOG_TABLE widget of a shared dashboard.
     *
     * <p>The application, severity filter and search text come from the stored widget
     * configuration; the caller only selects the time window and the page.
     */
    @Transactional(readOnly = true)
    public PageResponse<LogEventResponse> queryWidgetLogs(String token, Long widgetId, Instant from, Instant to,
                                                          Integer page, Integer size) {
        Dashboard dashboard = requireSharedDashboard(token);
        Widget widget = requireWidget(dashboard, widgetId);
        if (widget.getType() != WidgetType.LOG_TABLE) {
            throw new InvalidSharedQueryException("Widget " + widgetId + " is not a log table");
        }

        JsonNode config = readConfig(widget);
        LogsQueryRequest request = LogsQueryRequest.builder()
                .applicationId(readApplicationId(config))
                .level(readLevel(config))
                .from(from)
                .to(to)
                .searchText(readText(config, "searchText"))
                .page(page == null ? 0 : Math.max(page, 0))
                .size(size == null ? DEFAULT_LOG_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_LOG_PAGE_SIZE))
                .build();

        if (!request.isTimeRangeValid()) {
            throw new InvalidSharedQueryException(
                    "The requested time range is invalid or longer than " + MAX_RANGE_DAYS + " days");
        }

        return logQueryService.query(dashboard.getOrganizationId(), request);
    }

    private Dashboard requireSharedDashboard(String token) {
        if (token == null || token.isBlank()) {
            throw new SharedDashboardNotFoundException();
        }
        return dashboardRepository.findByShareToken(token)
                .filter(Dashboard::isPublic)
                .orElseThrow(SharedDashboardNotFoundException::new);
    }

    private Widget requireWidget(Dashboard dashboard, Long widgetId) {
        if (widgetId == null) {
            throw new SharedDashboardNotFoundException();
        }
        return widgetRepository.findByIdAndDashboard_Id(widgetId, dashboard.getId())
                .orElseThrow(SharedDashboardNotFoundException::new);
    }

    private JsonNode readConfig(Widget widget) {
        try {
            JsonNode node = objectMapper.readTree(widget.getConfig());
            if (node == null || !node.isObject()) {
                throw new InvalidSharedQueryException("Widget " + widget.getId() + " has an invalid configuration");
            }
            return node;
        } catch (InvalidSharedQueryException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidSharedQueryException("Widget " + widget.getId() + " has an invalid configuration");
        }
    }

    private Long readApplicationId(JsonNode config) {
        JsonNode node = config.get("applicationId");
        if (node == null || !node.isNumber()) {
            throw new InvalidSharedQueryException("Widget configuration has no valid applicationId");
        }
        return node.asLong();
    }

    private String readText(JsonNode config, String field) {
        JsonNode node = config.get(field);
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private MetricAggregation readAggregation(JsonNode config) {
        String value = readText(config, "aggregation");
        if (value == null) {
            return MetricAggregation.AVG;
        }
        try {
            return MetricAggregation.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidSharedQueryException("Widget configuration has an invalid aggregation");
        }
    }

    private Integer readIntervalMinutes(JsonNode config) {
        JsonNode node = config.get("intervalMinutes");
        if (node == null || !node.isNumber()) {
            return DEFAULT_INTERVAL_MINUTES;
        }
        int value = node.asInt();
        return value > 0 ? value : DEFAULT_INTERVAL_MINUTES;
    }

    private LogLevel readLevel(JsonNode config) {
        String value = readText(config, "level");
        if (value == null) {
            return null;
        }
        try {
            return LogLevel.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
