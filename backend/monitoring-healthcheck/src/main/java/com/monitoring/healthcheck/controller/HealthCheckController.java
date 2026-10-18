package com.monitoring.healthcheck.controller;

import com.monitoring.healthcheck.dto.CreateHealthCheckRequest;
import com.monitoring.healthcheck.dto.HealthCheckResponse;
import com.monitoring.healthcheck.dto.HealthCheckResultResponse;
import com.monitoring.healthcheck.dto.UpdateHealthCheckRequest;
import com.monitoring.healthcheck.service.HealthCheckService;
import com.monitoring.auth.organization.entity.MembershipRole;
import com.monitoring.auth.organization.security.RequireRole;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Health check CRUD and result history for authenticated users. The tenant scope (owner) is
 * resolved from the JWT principal, never from the request, and enforced in
 * {@link HealthCheckService}.
 */
@RestController
@RequestMapping("/api/v1/health-checks")
@RequiredArgsConstructor
public class HealthCheckController {

    private final HealthCheckService healthCheckService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<HealthCheckResponse>> list() {
        return ResponseEntity.ok(healthCheckService.list(currentTenantService.requireOrganizationId()));
    }

    @PostMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<HealthCheckResponse> create(@Valid @RequestBody CreateHealthCheckRequest request) {
        HealthCheckResponse created = healthCheckService.create(
                currentTenantService.requireOrganizationId(), request);
        return ResponseEntity.created(URI.create("/api/v1/health-checks/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<HealthCheckResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateHealthCheckRequest request) {
        return ResponseEntity.ok(healthCheckService.update(
                currentTenantService.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        healthCheckService.delete(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<List<HealthCheckResultResponse>> results(
            @PathVariable Long id,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(healthCheckService.listResults(
                currentTenantService.requireOrganizationId(), id, limit));
    }
}
