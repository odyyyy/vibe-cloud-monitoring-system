package com.monitoring.dashboards.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.dashboards.dto.CreateDashboardRequest;
import com.monitoring.dashboards.dto.CreateWidgetRequest;
import com.monitoring.dashboards.dto.DashboardResponse;
import com.monitoring.dashboards.dto.UpdateDashboardRequest;
import com.monitoring.dashboards.dto.UpdateWidgetRequest;
import com.monitoring.dashboards.dto.WidgetResponse;
import com.monitoring.dashboards.entity.Dashboard;
import com.monitoring.dashboards.entity.Widget;
import com.monitoring.dashboards.exception.DashboardAccessDeniedException;
import com.monitoring.dashboards.exception.DashboardNotFoundException;
import com.monitoring.dashboards.exception.InvalidWidgetConfigException;
import com.monitoring.dashboards.exception.WidgetLimitExceededException;
import com.monitoring.dashboards.exception.WidgetNotFoundException;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.dashboards.repository.WidgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for dashboards and their widgets.
 *
 * <p>Every operation is scoped to the authenticated {@code organizationId}; a dashboard that exists
 * but belongs to another user raises {@link DashboardAccessDeniedException} (HTTP 403), while
 * a missing one raises {@link DashboardNotFoundException} (HTTP 404).
 *
 * <p>Widget config is treated as an opaque JSON document: it is checked for being a valid
 * JSON object and never interpreted or executed.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    public static final int MAX_WIDGETS_PER_DASHBOARD = 20;

    private final DashboardRepository dashboardRepository;
    private final WidgetRepository widgetRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<DashboardResponse> listDashboards(Long organizationId) {
        return dashboardRepository.findAllWithWidgetsByOrganizationId(organizationId).stream()
                .map(DashboardResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long organizationId, Long dashboardId) {
        return DashboardResponse.fromEntity(getOwnedDashboard(organizationId, dashboardId));
    }

    @Transactional
    public DashboardResponse createDashboard(Long organizationId, CreateDashboardRequest request) {
        Dashboard dashboard = Dashboard.builder()
                .name(request.getName().trim())
                .organizationId(organizationId)
                .build();
        return DashboardResponse.fromEntity(dashboardRepository.save(dashboard));
    }

    @Transactional
    public DashboardResponse updateDashboard(Long organizationId, Long dashboardId, UpdateDashboardRequest request) {
        Dashboard dashboard = getOwnedDashboard(organizationId, dashboardId);
        dashboard.setName(request.getName().trim());
        return DashboardResponse.fromEntity(dashboardRepository.save(dashboard));
    }

    @Transactional
    public void deleteDashboard(Long organizationId, Long dashboardId) {
        dashboardRepository.delete(getOwnedDashboard(organizationId, dashboardId));
    }

    @Transactional
    public WidgetResponse addWidget(Long organizationId, Long dashboardId, CreateWidgetRequest request) {
        Dashboard dashboard = getOwnedDashboard(organizationId, dashboardId);

        if (widgetRepository.countByDashboard_Id(dashboardId) >= MAX_WIDGETS_PER_DASHBOARD) {
            throw new WidgetLimitExceededException(MAX_WIDGETS_PER_DASHBOARD);
        }

        Widget widget = Widget.builder()
                .dashboard(dashboard)
                .type(request.getType())
                .config(validateAndNormalizeConfig(request.getConfig()))
                .positionX(request.getPositionX())
                .positionY(request.getPositionY())
                .width(request.getWidth())
                .height(request.getHeight())
                .build();

        return WidgetResponse.fromEntity(widgetRepository.save(widget));
    }

    @Transactional
    public WidgetResponse updateWidget(Long organizationId, Long dashboardId, Long widgetId, UpdateWidgetRequest request) {
        getOwnedDashboard(organizationId, dashboardId);
        Widget widget = getWidget(widgetId, dashboardId);

        if (request.getType() != null) {
            widget.setType(request.getType());
        }
        if (request.getConfig() != null) {
            widget.setConfig(validateAndNormalizeConfig(request.getConfig()));
        }
        if (request.getPositionX() != null) {
            widget.setPositionX(request.getPositionX());
        }
        if (request.getPositionY() != null) {
            widget.setPositionY(request.getPositionY());
        }
        if (request.getWidth() != null) {
            widget.setWidth(request.getWidth());
        }
        if (request.getHeight() != null) {
            widget.setHeight(request.getHeight());
        }

        return WidgetResponse.fromEntity(widgetRepository.save(widget));
    }

    @Transactional
    public void deleteWidget(Long organizationId, Long dashboardId, Long widgetId) {
        getOwnedDashboard(organizationId, dashboardId);
        widgetRepository.delete(getWidget(widgetId, dashboardId));
    }

    private Widget getWidget(Long widgetId, Long dashboardId) {
        return widgetRepository.findByIdAndDashboard_Id(widgetId, dashboardId)
                .orElseThrow(() -> new WidgetNotFoundException(widgetId));
    }

    private Dashboard getOwnedDashboard(Long organizationId, Long dashboardId) {
        Dashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new DashboardNotFoundException(dashboardId));
        if (!dashboard.getOrganizationId().equals(organizationId)) {
            throw new DashboardAccessDeniedException();
        }
        return dashboard;
    }

    private String validateAndNormalizeConfig(String config) {
        String trimmed = config == null ? null : config.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new InvalidWidgetConfigException("config must not be blank");
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node == null || !node.isObject()) {
                throw new InvalidWidgetConfigException("config must be a JSON object");
            }
        } catch (JsonProcessingException e) {
            throw new InvalidWidgetConfigException("config must be valid JSON");
        }
        return trimmed;
    }
}
