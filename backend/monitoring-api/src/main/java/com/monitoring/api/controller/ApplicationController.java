package com.monitoring.api.controller;

import com.monitoring.apps.dto.ApplicationCreateRequest;
import com.monitoring.apps.dto.ApplicationResponse;
import com.monitoring.apps.dto.ApplicationUpdateRequest;
import com.monitoring.apps.dto.ApplicationWithKeyResponse;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.audit.annotation.AuditLog;
import com.monitoring.auth.organization.entity.MembershipRole;
import com.monitoring.auth.organization.security.RequireRole;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> listApplications() {
        List<ApplicationResponse> response = applicationService.findByOrganizationId(currentTenantService.requireOrganizationId())
                .stream()
                .map(ApplicationResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<ApplicationWithKeyResponse> createApplication(
            @Valid @RequestBody ApplicationCreateRequest request) {
        ApplicationWithKeyResponse application = applicationService.createApplication(
                request, currentTenantService.requireOrganizationId());
        URI location = URI.create("/api/applications/" + application.id());
        return ResponseEntity.created(location).body(application);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable Long id) {
        Application application = applicationService.getOwnedApplication(
                id, currentTenantService.requireOrganizationId());
        return ResponseEntity.ok(ApplicationResponse.fromEntity(application));
    }

    @PutMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<ApplicationResponse> updateApplication(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationUpdateRequest request) {
        Application application = applicationService.updateApplication(
                id, request.getName(), currentTenantService.requireOrganizationId());
        return ResponseEntity.ok(ApplicationResponse.fromEntity(application));
    }

    @DeleteMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "APPLICATION_DELETED", resourceId = "#id")
    public ResponseEntity<Void> deleteApplication(@PathVariable Long id) {
        applicationService.deleteApplication(id, currentTenantService.requireOrganizationId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Rotates the agent API key. The new plaintext key is returned once and is not stored,
     * so it cannot be retrieved again.
     */
    @PostMapping("/{id}/regenerate-api-key")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "API_KEY_REGENERATED", resourceId = "#id")
    public ResponseEntity<ApplicationWithKeyResponse> regenerateApiKey(@PathVariable Long id) {
        return ResponseEntity.ok(applicationService.regenerateApiKey(
                id, currentTenantService.requireOrganizationId()));
    }
}
