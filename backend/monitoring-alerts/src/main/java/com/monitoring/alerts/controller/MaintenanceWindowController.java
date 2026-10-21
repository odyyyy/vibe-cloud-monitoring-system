package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.request.CreateMaintenanceWindowRequest;
import com.monitoring.alerts.dto.request.UpdateMaintenanceWindowRequest;
import com.monitoring.alerts.dto.response.MaintenanceWindowResponse;
import com.monitoring.alerts.service.MaintenanceWindowService;
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
 * CRUD for alert maintenance windows. The tenant scope is resolved from the JWT principal, never
 * from the request, and enforced in {@link MaintenanceWindowService}.
 */
@RestController
@RequestMapping("/api/v1/maintenance-windows")
@RequiredArgsConstructor
public class MaintenanceWindowController {

    private final MaintenanceWindowService maintenanceWindowService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<MaintenanceWindowResponse>> list() {
        return ResponseEntity.ok(maintenanceWindowService.list(
                currentTenantService.requireOrganizationId()));
    }

    @PostMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<MaintenanceWindowResponse> create(
            @Valid @RequestBody CreateMaintenanceWindowRequest request) {
        MaintenanceWindowResponse created = maintenanceWindowService.create(
                currentTenantService.requireOrganizationId(), request);
        return ResponseEntity.created(URI.create("/api/v1/maintenance-windows/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<MaintenanceWindowResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMaintenanceWindowRequest request) {
        return ResponseEntity.ok(maintenanceWindowService.update(
                currentTenantService.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        maintenanceWindowService.delete(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
