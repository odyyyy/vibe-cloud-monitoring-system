package com.monitoring.dashboards.controller;

import com.monitoring.dashboards.dto.request.CreateDashboardRequest;
import com.monitoring.dashboards.dto.request.CreateWidgetRequest;
import com.monitoring.dashboards.dto.response.DashboardResponse;
import com.monitoring.dashboards.dto.request.UpdateDashboardRequest;
import com.monitoring.dashboards.dto.request.UpdateWidgetRequest;
import com.monitoring.dashboards.dto.response.WidgetResponse;
import com.monitoring.dashboards.service.DashboardService;
import com.monitoring.dashboards.service.SharedDashboardDataService;
import com.monitoring.audit.annotation.AuditLog;
import com.monitoring.collector.dto.response.AggregatedMetricResponse;
import com.monitoring.collector.dto.response.LogEventResponse;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.security.RequireRole;
import com.monitoring.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Dashboard and widget CRUD for authenticated users. The tenant scope (owner) is resolved
 * from the JWT principal, never from the request, and enforced in {@link DashboardService}.
 */
@RestController
@RequestMapping("/api/v1/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final SharedDashboardDataService sharedDashboardDataService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<DashboardResponse>> listDashboards() {
        return ResponseEntity.ok(dashboardService.listDashboards(currentTenantService.requireOrganizationId()));
    }

    @PostMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<DashboardResponse> createDashboard(
            @Valid @RequestBody CreateDashboardRequest request) {
        DashboardResponse created = dashboardService.createDashboard(
                currentTenantService.requireOrganizationId(), request);
        return ResponseEntity.created(URI.create("/api/v1/dashboards/" + created.getId())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DashboardResponse> getDashboard(@PathVariable Long id) {
        return ResponseEntity.ok(dashboardService.getDashboard(currentTenantService.requireOrganizationId(), id));
    }

    @PutMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<DashboardResponse> updateDashboard(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDashboardRequest request) {
        return ResponseEntity.ok(dashboardService.updateDashboard(
                currentTenantService.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "DASHBOARD_DELETED", resourceId = "#id")
    public ResponseEntity<Void> deleteDashboard(@PathVariable Long id) {
        dashboardService.deleteDashboard(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Generates or regenerates a public share token for the dashboard.
     * Returns the updated dashboard with the share token.
     */
    @PostMapping("/{id}/share")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "DASHBOARD_SHARED", resourceId = "#id")
    public ResponseEntity<DashboardResponse> generateShareToken(@PathVariable Long id) {
        DashboardResponse response = dashboardService.generateShareToken(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.ok(response);
    }

    /**
     * Revokes the public share token for the dashboard.
     * Returns the updated dashboard without the share token.
     */
    @DeleteMapping("/{id}/share")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "DASHBOARD_SHARE_REVOKED", resourceId = "#id")
    public ResponseEntity<DashboardResponse> revokeShareToken(@PathVariable Long id) {
        DashboardResponse response = dashboardService.revokeShareToken(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.ok(response);
    }

    /**
     * Public endpoint to retrieve a dashboard by its share token.
     * Does not require authentication. Returns 404 if token is invalid or dashboard is not public.
     */
    @GetMapping("/share/{token}")
    public ResponseEntity<DashboardResponse> getDashboardByShareToken(@PathVariable String token) {
        DashboardResponse dashboard = dashboardService.getDashboardByShareToken(token);
        if (dashboard == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Public endpoint returning the metric series of a widget of a shared dashboard.
     * The application and metric are taken from the stored widget configuration; the caller
     * only selects the time window.
     */
    @GetMapping("/share/{token}/widgets/{widgetId}/metrics")
    public ResponseEntity<List<AggregatedMetricResponse>> getSharedWidgetMetrics(
            @PathVariable String token,
            @PathVariable Long widgetId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(sharedDashboardDataService.queryWidgetMetrics(token, widgetId, from, to));
    }

    /**
     * Public endpoint returning the log rows of a widget of a shared dashboard.
     * The application and filters are taken from the stored widget configuration; the caller
     * only selects the time window and the page.
     */
    @GetMapping("/share/{token}/widgets/{widgetId}/logs")
    public ResponseEntity<PageResponse<LogEventResponse>> getSharedWidgetLogs(
            @PathVariable String token,
            @PathVariable Long widgetId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(
                sharedDashboardDataService.queryWidgetLogs(token, widgetId, from, to, page, size));
    }

    @PostMapping("/{id}/widgets")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<WidgetResponse> addWidget(
            @PathVariable Long id,
            @Valid @RequestBody CreateWidgetRequest request) {
        WidgetResponse created = dashboardService.addWidget(
                currentTenantService.requireOrganizationId(), id, request);
        return ResponseEntity.created(URI.create("/api/v1/dashboards/" + id + "/widgets/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}/widgets/{widgetId}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<WidgetResponse> updateWidget(
            @PathVariable Long id,
            @PathVariable Long widgetId,
            @Valid @RequestBody UpdateWidgetRequest request) {
        return ResponseEntity.ok(dashboardService.updateWidget(
                currentTenantService.requireOrganizationId(), id, widgetId, request));
    }

    @DeleteMapping("/{id}/widgets/{widgetId}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<Void> deleteWidget(@PathVariable Long id, @PathVariable Long widgetId) {
        dashboardService.deleteWidget(currentTenantService.requireOrganizationId(), id, widgetId);
        return ResponseEntity.noContent().build();
    }
}
