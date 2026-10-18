package com.monitoring.dashboards.controller;

import com.monitoring.dashboards.dto.request.CreateDashboardRequest;
import com.monitoring.dashboards.dto.request.CreateWidgetRequest;
import com.monitoring.dashboards.dto.response.DashboardResponse;
import com.monitoring.dashboards.dto.request.UpdateDashboardRequest;
import com.monitoring.dashboards.dto.request.UpdateWidgetRequest;
import com.monitoring.dashboards.dto.response.WidgetResponse;
import com.monitoring.dashboards.service.DashboardService;
import com.monitoring.audit.annotation.AuditLog;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.security.RequireRole;
import com.monitoring.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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
