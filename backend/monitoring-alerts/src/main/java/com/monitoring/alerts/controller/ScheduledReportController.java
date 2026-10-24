package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.request.ScheduledReportRequest;
import com.monitoring.alerts.dto.response.ScheduledReportPreviewResponse;
import com.monitoring.alerts.dto.response.ScheduledReportResponse;
import com.monitoring.alerts.service.ScheduledReportService;
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

/** Tenant-scoped CRUD and test delivery for scheduled email/Slack reports. */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ScheduledReportController {

    private final ScheduledReportService reportService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<ScheduledReportResponse>> list() {
        return ResponseEntity.ok(reportService.list(currentTenantService.requireOrganizationId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduledReportResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.get(currentTenantService.requireOrganizationId(), id));
    }

    @PostMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "SCHEDULED_REPORT_CREATED")
    public ResponseEntity<ScheduledReportResponse> create(
            @Valid @RequestBody ScheduledReportRequest request) {
        ScheduledReportResponse created =
                reportService.create(currentTenantService.requireOrganizationId(), request);
        return ResponseEntity.created(URI.create("/api/v1/reports/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "SCHEDULED_REPORT_UPDATED", resourceId = "#id")
    public ResponseEntity<ScheduledReportResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ScheduledReportRequest request) {
        return ResponseEntity.ok(
                reportService.update(currentTenantService.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "SCHEDULED_REPORT_DELETED", resourceId = "#id")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reportService.delete(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Generates the current report and makes one delivery attempt, returning the exact rendered
     * payload. A failed attempt still returns the preview with the delivery error.
     */
    @PostMapping("/{id}/test")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "SCHEDULED_REPORT_TESTED", resourceId = "#id")
    public ResponseEntity<ScheduledReportPreviewResponse> test(@PathVariable Long id) {
        return ResponseEntity.ok(
                reportService.sendTest(currentTenantService.requireOrganizationId(), id));
    }
}
