package com.monitoring.dashboards.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.dashboards.dto.DashboardVariable;
import com.monitoring.dashboards.dto.request.CreateDashboardRequest;
import com.monitoring.dashboards.dto.request.CreateWidgetRequest;
import com.monitoring.dashboards.dto.response.DashboardResponse;
import com.monitoring.dashboards.dto.request.UpdateDashboardRequest;
import com.monitoring.dashboards.dto.request.UpdateWidgetRequest;
import com.monitoring.dashboards.dto.response.WidgetResponse;
import com.monitoring.dashboards.entity.Dashboard;
import com.monitoring.dashboards.entity.Widget;
import com.monitoring.dashboards.exception.DashboardAccessDeniedException;
import com.monitoring.dashboards.exception.DashboardNotFoundException;
import com.monitoring.dashboards.exception.InvalidDashboardVariableException;
import com.monitoring.dashboards.exception.InvalidWidgetConfigException;
import com.monitoring.dashboards.exception.WidgetLimitExceededException;
import com.monitoring.dashboards.exception.WidgetNotFoundException;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.dashboards.repository.WidgetRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Application service for dashboards and their widgets.
 *
 * <p>Every operation is scoped to the authenticated {@code organizationId}; a dashboard that exists
 * but belongs to another user raises {@link DashboardAccessDeniedException} (HTTP 403), while
 * a missing one raises {@link DashboardNotFoundException} (HTTP 404).
 *
 * <p>Widget config and template variable definitions are treated as opaque JSON documents: they
 * are checked for being valid JSON and are never interpreted or executed.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    public static final int MAX_WIDGETS_PER_DASHBOARD = 20;
    public static final int MAX_VARIABLES_PER_DASHBOARD = 20;

    private static final TypeReference<List<DashboardVariable>> VARIABLE_LIST_TYPE = new TypeReference<>() {
    };

    private final DashboardRepository dashboardRepository;
    private final WidgetRepository widgetRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<DashboardResponse> listDashboards(Long organizationId) {
        return dashboardRepository.findAllWithWidgetsByOrganizationId(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long organizationId, Long dashboardId) {
        return toResponse(getOwnedDashboard(organizationId, dashboardId));
    }

    @Transactional
    public DashboardResponse createDashboard(Long organizationId, CreateDashboardRequest request) {
        Dashboard dashboard = Dashboard.builder()
                .name(request.getName().trim())
                .organizationId(organizationId)
                .build();
        return toResponse(dashboardRepository.save(dashboard));
    }

    @Transactional
    public DashboardResponse updateDashboard(Long organizationId, Long dashboardId, UpdateDashboardRequest request) {
        Dashboard dashboard = getOwnedDashboard(organizationId, dashboardId);
        dashboard.setName(request.getName().trim());
        if (request.getVariables() != null) {
            dashboard.setVariables(validateAndSerializeVariables(request.getVariables()));
        }
        return toResponse(dashboardRepository.save(dashboard));
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

    private DashboardResponse toResponse(Dashboard dashboard) {
        return DashboardResponse.fromEntity(dashboard, parseVariables(dashboard.getVariables()));
    }

    /** Parses stored definitions; a corrupted document degrades to no variables instead of failing reads. */
    private List<DashboardVariable> parseVariables(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<DashboardVariable> variables = objectMapper.readValue(json, VARIABLE_LIST_TYPE);
            return variables == null ? List.of() : variables;
        } catch (JsonProcessingException e) {
            log.warn("Ignoring unreadable dashboard variables document");
            return List.of();
        }
    }

    /**
     * Validates variable definitions and serializes them to a JSON array. Bean Validation has
     * already checked field shapes; this enforces the cross-field rules and duplicate names.
     */
    private String validateAndSerializeVariables(List<DashboardVariable> variables) {
        if (variables.size() > MAX_VARIABLES_PER_DASHBOARD) {
            throw new InvalidDashboardVariableException(
                    "a dashboard may define at most " + MAX_VARIABLES_PER_DASHBOARD + " variables");
        }

        Set<String> names = new HashSet<>();
        for (DashboardVariable variable : variables) {
            if (!names.add(variable.getName())) {
                throw new InvalidDashboardVariableException("duplicate variable name: " + variable.getName());
            }
            String defaultValue = variable.getDefaultValue();
            if (defaultValue != null && !defaultValue.isEmpty() && !variable.getOptions().contains(defaultValue)) {
                throw new InvalidDashboardVariableException(
                        "default value for '" + variable.getName() + "' must be one of its options");
            }
        }

        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize dashboard variables", e);
        }
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
