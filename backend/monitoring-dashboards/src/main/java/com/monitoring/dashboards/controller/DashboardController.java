package com.monitoring.dashboards.controller;

import com.monitoring.dashboards.dto.CreateDashboardRequest;
import com.monitoring.dashboards.dto.CreateWidgetRequest;
import com.monitoring.dashboards.dto.DashboardResponse;
import com.monitoring.dashboards.dto.UpdateDashboardRequest;
import com.monitoring.dashboards.dto.UpdateWidgetRequest;
import com.monitoring.dashboards.dto.WidgetResponse;
import com.monitoring.dashboards.service.DashboardService;
import com.monitoring.audit.annotation.AuditLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @GetMapping
    public ResponseEntity<List<DashboardResponse>> listDashboards() {
        return ResponseEntity.ok(dashboardService.listDashboards(currentUserId()));
    }

    @PostMapping
    public ResponseEntity<DashboardResponse> createDashboard(
            @Valid @RequestBody CreateDashboardRequest request) {
        DashboardResponse created = dashboardService.createDashboard(currentUserId(), request);
        return ResponseEntity.created(URI.create("/api/v1/dashboards/" + created.getId())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DashboardResponse> getDashboard(@PathVariable Long id) {
        return ResponseEntity.ok(dashboardService.getDashboard(currentUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DashboardResponse> updateDashboard(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDashboardRequest request) {
        return ResponseEntity.ok(dashboardService.updateDashboard(currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    @AuditLog(action = "DASHBOARD_DELETED", resourceId = "#id")
    public ResponseEntity<Void> deleteDashboard(@PathVariable Long id) {
        dashboardService.deleteDashboard(currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/widgets")
    public ResponseEntity<WidgetResponse> addWidget(
            @PathVariable Long id,
            @Valid @RequestBody CreateWidgetRequest request) {
        WidgetResponse created = dashboardService.addWidget(currentUserId(), id, request);
        return ResponseEntity.created(URI.create("/api/v1/dashboards/" + id + "/widgets/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}/widgets/{widgetId}")
    public ResponseEntity<WidgetResponse> updateWidget(
            @PathVariable Long id,
            @PathVariable Long widgetId,
            @Valid @RequestBody UpdateWidgetRequest request) {
        return ResponseEntity.ok(dashboardService.updateWidget(currentUserId(), id, widgetId, request));
    }

    @DeleteMapping("/{id}/widgets/{widgetId}")
    public ResponseEntity<Void> deleteWidget(@PathVariable Long id, @PathVariable Long widgetId) {
        dashboardService.deleteWidget(currentUserId(), id, widgetId);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;

        if (principal instanceof String userId) {
            try {
                return Long.parseLong(userId);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid user ID in authentication token");
            }
        }
        throw new IllegalStateException("No authenticated user available");
    }
}
