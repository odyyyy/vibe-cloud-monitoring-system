package com.monitoring.dashboards.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.dashboards.dto.DashboardVariable;
import com.monitoring.dashboards.dto.request.CreateDashboardRequest;
import com.monitoring.dashboards.dto.request.CreateWidgetRequest;
import com.monitoring.dashboards.dto.response.DashboardResponse;
import com.monitoring.dashboards.dto.request.UpdateDashboardRequest;
import com.monitoring.dashboards.dto.request.UpdateWidgetRequest;
import com.monitoring.dashboards.dto.response.WidgetResponse;
import com.monitoring.dashboards.dto.snapshot.DashboardSnapshot;
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
import com.monitoring.revisions.dto.ConfigRevisionResponse;
import com.monitoring.revisions.dto.ConfigRevisionSummaryResponse;
import com.monitoring.revisions.entity.ConfigResourceType;
import com.monitoring.revisions.entity.ConfigRevision;
import com.monitoring.revisions.exception.ConfigRevisionRestoreConflictException;
import com.monitoring.revisions.exception.InvalidConfigSnapshotException;
import com.monitoring.revisions.service.ConfigRevisionService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    /** Current dashboard snapshot shape; bump when the stored fields change incompatibly. */
    static final int SNAPSHOT_SCHEMA_VERSION = 1;

    private static final TypeReference<List<DashboardVariable>> VARIABLE_LIST_TYPE = new TypeReference<>() {
    };
    private static final ConfigResourceType RESOURCE_TYPE = ConfigResourceType.DASHBOARD;

    private final DashboardRepository dashboardRepository;
    private final WidgetRepository widgetRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationService applicationService;
    private final ConfigRevisionService revisionService;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<DashboardResponse> listDashboards(Long organizationId) {
        return dashboardRepository.findAllWithWidgetsByOrganizationId(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long organizationId, Long dashboardId) {
        return getDashboard(organizationId, dashboardId, false);
    }

    /**
     * Reads a dashboard. A tenant sees only its own dashboards; a platform administrator may
     * additionally read a system dashboard.
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long organizationId, Long dashboardId, boolean platformAdmin) {
        Dashboard dashboard = findDashboard(dashboardId);
        if (dashboard.isSystem()) {
            if (!platformAdmin) {
                throw new DashboardAccessDeniedException();
            }
            return toResponse(dashboard);
        }
        if (!dashboard.getOrganizationId().equals(organizationId)) {
            throw new DashboardAccessDeniedException();
        }
        return toResponse(dashboard);
    }

    /**
     * Loads a platform-managed dashboard for the administration API. The caller is authorized
     * by {@code @PreAuthorize("hasRole('PLATFORM_ADMIN')")} on the controller.
     */
    @Transactional(readOnly = true)
    public DashboardResponse getSystemDashboard(String systemDashboardType) {
        Dashboard dashboard = dashboardRepository.findBySystemDashboardType(systemDashboardType)
                .orElseThrow(() -> new DashboardNotFoundException(
                        "System dashboard '" + systemDashboardType + "' was not found"));
        return toResponse(dashboard);
    }

    @Transactional
    public DashboardResponse createDashboard(Long organizationId, CreateDashboardRequest request) {
        Dashboard dashboard = Dashboard.builder()
                .name(request.getName().trim())
                .organizationId(organizationId)
                .build();
        Dashboard saved = dashboardRepository.save(dashboard);
        revisionService.recordCreate(organizationId, RESOURCE_TYPE, saved.getId(),
                SNAPSHOT_SCHEMA_VERSION, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public DashboardResponse updateDashboard(Long organizationId,
                                             Long dashboardId,
                                             UpdateDashboardRequest request) {
        return updateDashboard(organizationId, dashboardId, request, false);
    }

    @Transactional
    public DashboardResponse updateDashboard(Long organizationId,
                                             Long dashboardId,
                                             UpdateDashboardRequest request,
                                             boolean platformAdmin) {
        Dashboard dashboard = getEditableDashboard(organizationId, dashboardId, platformAdmin);
        String before = dashboard.isSystem() ? null : snapshot(dashboard);
        dashboard.setName(request.getName().trim());
        if (request.getVariables() != null) {
            dashboard.setVariables(validateAndSerializeVariables(request.getVariables()));
        }
        Dashboard saved = dashboardRepository.save(dashboard);
        if (!dashboard.isSystem()) {
            revisionService.recordUpdate(organizationId, RESOURCE_TYPE, dashboardId,
                    SNAPSHOT_SCHEMA_VERSION, before, snapshot(saved));
        }
        return toResponse(saved);
    }

    @Transactional
    public void deleteDashboard(Long organizationId, Long dashboardId) {
        deleteDashboard(organizationId, dashboardId, false);
    }

    @Transactional
    public void deleteDashboard(Long organizationId, Long dashboardId, boolean platformAdmin) {
        Dashboard dashboard = getEditableDashboard(organizationId, dashboardId, platformAdmin);
        revisionService.deleteAll(organizationId, RESOURCE_TYPE, dashboardId);
        dashboardRepository.delete(dashboard);
    }

    @Transactional
    public WidgetResponse addWidget(Long organizationId,
                                    Long dashboardId,
                                    CreateWidgetRequest request) {
        return addWidget(organizationId, dashboardId, request, false);
    }

    @Transactional
    public WidgetResponse addWidget(Long organizationId,
                                    Long dashboardId,
                                    CreateWidgetRequest request,
                                    boolean platformAdmin) {
        Dashboard dashboard = getEditableDashboard(organizationId, dashboardId, platformAdmin);
        String before = dashboard.isSystem() ? null : snapshot(dashboard);
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

        Widget saved = widgetRepository.save(widget);
        if (!dashboard.isSystem()) {
            revisionService.recordUpdate(organizationId, RESOURCE_TYPE, dashboardId,
                    SNAPSHOT_SCHEMA_VERSION, before, snapshot(dashboard));
        }
        return WidgetResponse.fromEntity(saved);
    }

    @Transactional
    public WidgetResponse updateWidget(Long organizationId,
                                       Long dashboardId,
                                       Long widgetId,
                                       UpdateWidgetRequest request) {
        return updateWidget(organizationId, dashboardId, widgetId, request, false);
    }

    @Transactional
    public WidgetResponse updateWidget(Long organizationId,
                                       Long dashboardId,
                                       Long widgetId,
                                       UpdateWidgetRequest request,
                                       boolean platformAdmin) {
        Dashboard dashboard = getEditableDashboard(organizationId, dashboardId, platformAdmin);
        String before = dashboard.isSystem() ? null : snapshot(dashboard);
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

        Widget saved = widgetRepository.save(widget);
        if (!dashboard.isSystem()) {
            revisionService.recordUpdate(organizationId, RESOURCE_TYPE, dashboardId,
                    SNAPSHOT_SCHEMA_VERSION, before, snapshot(dashboard));
        }
        return WidgetResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteWidget(Long organizationId, Long dashboardId, Long widgetId) {
        deleteWidget(organizationId, dashboardId, widgetId, false);
    }

    @Transactional
    public void deleteWidget(Long organizationId, Long dashboardId, Long widgetId, boolean platformAdmin) {
        Dashboard dashboard = getEditableDashboard(organizationId, dashboardId, platformAdmin);
        String before = dashboard.isSystem() ? null : snapshot(dashboard);
        widgetRepository.delete(getWidget(widgetId, dashboardId));
        if (!dashboard.isSystem()) {
            revisionService.recordUpdate(organizationId, RESOURCE_TYPE, dashboardId,
                    SNAPSHOT_SCHEMA_VERSION, before, snapshot(dashboard));
        }
    }

    /**
     * Generates or regenerates a public share token for the dashboard.
     * Only ADMIN/OWNER of the organization can call this.
     */
    @Transactional
    public DashboardResponse generateShareToken(Long organizationId, Long dashboardId) {
        Dashboard dashboard = getTenantDashboard(organizationId, dashboardId);
        dashboard.generateShareToken();
        return toResponse(dashboardRepository.save(dashboard));
    }

    /**
     * Revokes the public share token for the dashboard.
     * Only ADMIN/OWNER of the organization can call this.
     */
    @Transactional
    public DashboardResponse revokeShareToken(Long organizationId, Long dashboardId) {
        Dashboard dashboard = getTenantDashboard(organizationId, dashboardId);
        dashboard.revokeShareToken();
        return toResponse(dashboardRepository.save(dashboard));
    }

    /**
     * Retrieves a dashboard by its public share token.
     * This bypasses organization scoping - access is controlled by the unguessable token.
     * Returns null if token is invalid, dashboard is not public, or dashboard doesn't exist.
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboardByShareToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return dashboardRepository.findByShareToken(token)
                .map(this::toPublicResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConfigRevisionSummaryResponse> listRevisions(Long organizationId,
                                                                     Long dashboardId,
                                                                     Integer page,
                                                                     Integer size) {
        getTenantDashboard(organizationId, dashboardId);
        return revisionService.list(organizationId, RESOURCE_TYPE, dashboardId, page, size);
    }

    @Transactional(readOnly = true)
    public ConfigRevisionResponse getRevision(Long organizationId,
                                              Long dashboardId,
                                              int revisionNumber) {
        getTenantDashboard(organizationId, dashboardId);
        return revisionService.get(organizationId, RESOURCE_TYPE, dashboardId, revisionNumber);
    }

    /**
     * Restores a previous dashboard snapshot. The snapshot is revalidated against current
     * limits and all widget application references must still be accessible; the restore is
     * then recorded as a new revision, leaving the history intact.
     */
    @Transactional
    public DashboardResponse restoreRevision(Long organizationId,
                                             Long dashboardId,
                                             int revisionNumber) {
        Dashboard dashboard = getTenantDashboard(organizationId, dashboardId);
        ConfigRevision revision =
                revisionService.requireRevision(organizationId, RESOURCE_TYPE, dashboardId, revisionNumber);
        DashboardSnapshot snapshot = readSnapshot(revision);

        UpdateDashboardRequest update = new UpdateDashboardRequest();
        update.setName(snapshot.name());
        update.setVariables(snapshot.variables() == null ? List.of() : snapshot.variables());
        validateRestoreRequest(update);

        String serializedVariables;
        try {
            serializedVariables = validateAndSerializeVariables(update.getVariables());
        } catch (InvalidDashboardVariableException ex) {
            throw new InvalidConfigSnapshotException(ex.getMessage(), ex);
        }

        List<Widget> widgets = buildRestoredWidgets(snapshot.widgets());
        // The restored widgets belong to the dashboard they are restored into.
        widgets.forEach(widget -> widget.setDashboard(dashboard));
        ensureReferencedApplicationsExist(organizationId, widgets);

        dashboard.setName(update.getName().trim());
        dashboard.setVariables(serializedVariables);
        dashboard.getWidgets().clear();
        dashboard.getWidgets().addAll(widgets);
        Dashboard saved = dashboardRepository.saveAndFlush(dashboard);

        revisionService.recordRestore(organizationId, RESOURCE_TYPE, dashboardId,
                revisionNumber, SNAPSHOT_SCHEMA_VERSION, snapshot(saved));
        return toResponse(saved);
    }

    private String snapshot(Dashboard dashboard) {
        List<Widget> widgets =
                widgetRepository.findByDashboard_IdOrderByPositionYAscPositionXAscIdAsc(dashboard.getId());
        DashboardSnapshot snapshot = new DashboardSnapshot(
                dashboard.getName(),
                parseVariables(dashboard.getVariables()),
                widgets.stream()
                        .map(widget -> new DashboardSnapshot.WidgetSnapshot(
                                widget.getType(),
                                widget.getConfig(),
                                widget.getPositionX(),
                                widget.getPositionY(),
                                widget.getWidth(),
                                widget.getHeight()))
                        .toList());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize dashboard snapshot", e);
        }
    }

    private DashboardSnapshot readSnapshot(ConfigRevision revision) {
        if (revision.getSchemaVersion() != SNAPSHOT_SCHEMA_VERSION) {
            throw new InvalidConfigSnapshotException(
                    "Dashboard snapshot schema " + revision.getSchemaVersion() + " is not supported");
        }
        try {
            DashboardSnapshot snapshot = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
                    .readValue(revision.getSnapshot(), DashboardSnapshot.class);
            if (snapshot == null) {
                throw new InvalidConfigSnapshotException("Dashboard snapshot is empty");
            }
            return snapshot;
        } catch (JsonProcessingException e) {
            throw new InvalidConfigSnapshotException("Dashboard snapshot is not valid", e);
        }
    }

    private void validateRestoreRequest(UpdateDashboardRequest request) {
        Set<ConstraintViolation<UpdateDashboardRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new InvalidConfigSnapshotException(
                    "Dashboard snapshot is invalid: " + violations.iterator().next().getMessage());
        }
    }

    private List<Widget> buildRestoredWidgets(List<DashboardSnapshot.WidgetSnapshot> snapshots) {
        if (snapshots == null) {
            return new ArrayList<>();
        }
        if (snapshots.size() > MAX_WIDGETS_PER_DASHBOARD) {
            throw new InvalidConfigSnapshotException(
                    "Dashboard snapshot exceeds the widget limit of " + MAX_WIDGETS_PER_DASHBOARD);
        }

        List<Widget> widgets = new ArrayList<>();
        for (DashboardSnapshot.WidgetSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                throw new InvalidConfigSnapshotException("Dashboard snapshot contains an empty widget");
            }
            CreateWidgetRequest request = CreateWidgetRequest.builder()
                    .type(snapshot.type())
                    .config(snapshot.config())
                    .positionX(snapshot.positionX())
                    .positionY(snapshot.positionY())
                    .width(snapshot.width())
                    .height(snapshot.height())
                    .build();
            Set<ConstraintViolation<CreateWidgetRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new InvalidConfigSnapshotException(
                        "Dashboard snapshot is invalid: " + violations.iterator().next().getMessage());
            }

            String config;
            try {
                config = validateAndNormalizeConfig(snapshot.config());
            } catch (InvalidWidgetConfigException ex) {
                throw new InvalidConfigSnapshotException(ex.getMessage(), ex);
            }

            widgets.add(Widget.builder()
                    .type(snapshot.type())
                    .config(config)
                    .positionX(snapshot.positionX())
                    .positionY(snapshot.positionY())
                    .width(snapshot.width())
                    .height(snapshot.height())
                    .build());
        }
        return widgets;
    }

    /**
     * Dashboard widgets may point at an application. Restoring a snapshot whose application was
     * deleted or belongs to another tenant would create a broken widget, so it is rejected.
     */
    private void ensureReferencedApplicationsExist(Long organizationId, List<Widget> widgets) {
        Set<Long> applicationIds = new LinkedHashSet<>();
        for (Widget widget : widgets) {
            try {
                JsonNode applicationId = objectMapper.readTree(widget.getConfig()).get("applicationId");
                if (applicationId == null) {
                    continue;
                }
                long id;
                if (applicationId.isIntegralNumber()) {
                    id = applicationId.asLong();
                } else if (applicationId.isTextual()) {
                    id = Long.parseLong(applicationId.asText());
                } else {
                    continue;
                }
                if (id > 0) {
                    applicationIds.add(id);
                }
            } catch (JsonProcessingException | NumberFormatException ignored) {
                // The config already passed JSON validation; a non-numeric reference is opaque.
            }
        }

        List<Long> missing = new ArrayList<>();
        for (Long applicationId : applicationIds) {
            try {
                applicationService.getOwnedApplication(applicationId, organizationId);
            } catch (RuntimeException ex) {
                missing.add(applicationId);
            }
        }
        if (!missing.isEmpty()) {
            throw new ConfigRevisionRestoreConflictException(
                    "Cannot restore dashboard: referenced application(s) no longer exist: " + missing);
        }
    }

    private Widget getWidget(Long widgetId, Long dashboardId) {
        return widgetRepository.findByIdAndDashboard_Id(widgetId, dashboardId)
                .orElseThrow(() -> new WidgetNotFoundException(widgetId));
    }

    /**
     * Loads a customer dashboard for a read or a share operation. System dashboards are not
     * reachable through the regular dashboard API, even when their id is known.
     */
    private Dashboard getTenantDashboard(Long organizationId, Long dashboardId) {
        Dashboard dashboard = findDashboard(dashboardId);
        if (dashboard.isSystem() || !dashboard.getOrganizationId().equals(organizationId)) {
            throw new DashboardAccessDeniedException();
        }
        return dashboard;
    }

    /**
     * Loads a dashboard for a mutation. A tenant may change only its own dashboards; a platform
     * administrator may additionally change a system dashboard.
     */
    private Dashboard getEditableDashboard(Long organizationId, Long dashboardId, boolean platformAdmin) {
        Dashboard dashboard = findDashboard(dashboardId);
        if (dashboard.isSystem()) {
            if (!platformAdmin) {
                throw new DashboardAccessDeniedException();
            }
            return dashboard;
        }
        if (!dashboard.getOrganizationId().equals(organizationId)) {
            throw new DashboardAccessDeniedException();
        }
        return dashboard;
    }

    private Dashboard findDashboard(Long dashboardId) {
        return dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new DashboardNotFoundException(dashboardId));
    }

    private DashboardResponse toResponse(Dashboard dashboard) {
        return DashboardResponse.fromEntity(dashboard, parseVariables(dashboard.getVariables()));
    }

    /**
     * Creates a public response without sensitive fields like organizationId.
     * Used for the public share endpoint.
     */
    private DashboardResponse toPublicResponse(Dashboard dashboard) {
        return DashboardResponse.fromEntityPublic(dashboard, parseVariables(dashboard.getVariables()));
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
