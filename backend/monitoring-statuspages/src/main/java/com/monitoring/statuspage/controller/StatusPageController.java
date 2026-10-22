package com.monitoring.statuspage.controller;

import com.monitoring.audit.annotation.AuditLog;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.security.RequireRole;
import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.statuspage.dto.request.CreateStatusPageRequest;
import com.monitoring.statuspage.dto.request.UpdateStatusPageRequest;
import com.monitoring.statuspage.dto.response.StatusPageResponse;
import com.monitoring.statuspage.service.StatusPageService;
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

@RestController
@RequestMapping("/api/v1/status-pages")
@RequiredArgsConstructor
public class StatusPageController {

    private final StatusPageService statusPageService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<StatusPageResponse>> list() {
        return ResponseEntity.ok(statusPageService.list(currentTenantService.requireOrganizationId()));
    }

    @PostMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "STATUS_PAGE_CREATED", resourceId = "#request.slug")
    public ResponseEntity<StatusPageResponse> create(@Valid @RequestBody CreateStatusPageRequest request) {
        StatusPageResponse created = statusPageService.create(
                currentTenantService.requireOrganizationId(), request);
        return ResponseEntity.created(URI.create("/api/v1/status-pages/" + created.getId())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StatusPageResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(statusPageService.get(currentTenantService.requireOrganizationId(), id));
    }

    @PutMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "STATUS_PAGE_UPDATED", resourceId = "#id")
    public ResponseEntity<StatusPageResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusPageRequest request) {
        return ResponseEntity.ok(statusPageService.update(
                currentTenantService.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "STATUS_PAGE_DELETED", resourceId = "#id")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        statusPageService.delete(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
