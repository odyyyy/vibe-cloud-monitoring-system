package com.monitoring.api.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.apps.security.ApiKeyHasher;
import com.monitoring.dashboards.entity.Dashboard;
import com.monitoring.dashboards.entity.Widget;
import com.monitoring.dashboards.entity.WidgetType;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.organization.entity.Organization;
import com.monitoring.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and owns the resources used for platform self-monitoring.
 *
 * <p>The hidden System organization and its internal application are the dogfooding data
 * source. The built-in dashboard is flagged {@code isSystem} with a stable
 * {@code systemDashboardType}; it is exposed read-only only through the platform-admin API.
 */
@Service
@RequiredArgsConstructor
public class PlatformResourceService {

    public static final String SYSTEM_ORGANIZATION_NAME = "System";
    public static final String SYSTEM_APPLICATION_NAME = "Platform";
    public static final String PLATFORM_DASHBOARD_NAME = "Platform Health";
    public static final String PLATFORM_HEALTH_DASHBOARD_TYPE = "PLATFORM_HEALTH";

    public static final String JVM_HEAP_METRIC = "jvm_memory_used_bytes";
    public static final String TOMCAT_BUSY_METRIC = "tomcat_threads_busy";
    public static final String DB_CONNECTIONS_METRIC = "hikaricp_connections_active";
    public static final String INGEST_REQUESTS_METRIC = "ingest_requests_total";
    public static final String INGEST_LATENCY_METRIC = "ingest_request_duration_seconds";

    private final OrganizationRepository organizationRepository;
    private final ApplicationRepository applicationRepository;
    private final DashboardRepository dashboardRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Application ensureSystemApplication() {
        Organization organization = organizationRepository.findFirstByIsSystemTrue()
                .orElseGet(() -> organizationRepository.save(Organization.builder()
                        .name(SYSTEM_ORGANIZATION_NAME)
                        .isSystem(true)
                        .build()));

        return applicationRepository.findFirstByIsSystemTrue()
                .orElseGet(() -> applicationRepository.save(Application.builder()
                        .name(SYSTEM_APPLICATION_NAME)
                        .apiKeyHash(ApiKeyHasher.hash("system_" + UUID.randomUUID()))
                        .organizationId(organization.getId())
                        .isSystem(true)
                        .build()));
    }

    /**
     * Creates the built-in dashboard once. Existing installations keep their current
     * definition; the bootstrap never overwrites user-visible configuration.
     */
    @Transactional
    public Dashboard ensurePlatformDashboard(Application systemApplication) {
        return dashboardRepository.findBySystemDashboardType(PLATFORM_HEALTH_DASHBOARD_TYPE)
                .orElseGet(() -> {
                    Dashboard dashboard = dashboardRepository.save(Dashboard.builder()
                            .name(PLATFORM_DASHBOARD_NAME)
                            .organizationId(systemApplication.getOrganizationId())
                            .isSystem(true)
                            .systemDashboardType(PLATFORM_HEALTH_DASHBOARD_TYPE)
                            .build());

                    List<Widget> widgets = buildWidgets(dashboard, systemApplication.getId());
                    dashboard.getWidgets().addAll(widgets);
                    return dashboardRepository.save(dashboard);
                });
    }

    private List<Widget> buildWidgets(Dashboard dashboard, Long applicationId) {
        List<Widget> widgets = new ArrayList<>();

        widgets.add(widget(dashboard, WidgetType.GAUGE, 0, 0, 6, 8,
                gaugeConfig(applicationId, JVM_HEAP_METRIC, "JVM Heap Usage", "bytes")));
        widgets.add(widget(dashboard, WidgetType.GAUGE, 6, 0, 6, 8,
                gaugeConfig(applicationId, DB_CONNECTIONS_METRIC, "Active DB Connections", "connections")));
        widgets.add(widget(dashboard, WidgetType.METRIC_CHART, 0, 8, 6, 10,
                chartConfig(applicationId, INGEST_REQUESTS_METRIC, "SUM", 1, "status")));
        widgets.add(widget(dashboard, WidgetType.METRIC_CHART, 6, 8, 6, 10,
                chartConfig(applicationId, INGEST_LATENCY_METRIC, "MAX", 5, null)));

        return widgets;
    }

    private Widget widget(Dashboard dashboard,
                          WidgetType type,
                          int positionX,
                          int positionY,
                          int width,
                          int height,
                          String config) {
        return Widget.builder()
                .dashboard(dashboard)
                .type(type)
                .config(config)
                .positionX(positionX)
                .positionY(positionY)
                .width(width)
                .height(height)
                .build();
    }

    private String gaugeConfig(Long applicationId, String metricName, String label, String unit) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("applicationId", applicationId);
        config.put("metricName", metricName);
        config.put("label", label);
        config.put("unit", unit);
        config.put("timeRangeHours", 1);
        return writeJson(config);
    }

    private String chartConfig(Long applicationId,
                               String metricName,
                               String aggregation,
                               int intervalMinutes,
                               String groupByTag) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("applicationId", applicationId);
        config.put("metricName", metricName);
        config.put("aggregation", aggregation);
        config.put("intervalMinutes", intervalMinutes);
        config.put("timeRangeHours", 1);
        if (groupByTag != null) {
            config.put("groupByTag", groupByTag);
        }
        return writeJson(config);
    }

    private String writeJson(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build platform dashboard configuration", e);
        }
    }
}
