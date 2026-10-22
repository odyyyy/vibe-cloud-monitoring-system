package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.response.AlertRuleResponse;
import com.monitoring.alerts.dto.response.LogCountPreviewResponse;
import com.monitoring.alerts.dto.request.CreateAlertRuleRequest;
import com.monitoring.alerts.dto.request.PreviewLogCountRequest;
import com.monitoring.alerts.dto.request.UpdateAlertRuleRequest;
import com.monitoring.alerts.service.AlertService;
import com.monitoring.audit.annotation.AuditLog;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.security.RequireRole;
import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.revisions.dto.ConfigRevisionResponse;
import com.monitoring.revisions.dto.ConfigRevisionSummaryResponse;
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
 * Alert rule CRUD for authenticated users. The tenant scope (owner) is resolved from the JWT
 * principal, never from the request, and enforced in {@link AlertService}.
 */
@RestController
@RequestMapping("/api/v1/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertService alertService;
    private final CurrentTenantService currentTenantService;

    /**
     * Lists the tenant's rules. The optional {@code applicationId} and {@code metricName} filters
     * back the chart threshold overlay: they narrow the result to active metric rules for one
     * application that reference a given metric. The tenant scope is still resolved from the JWT.
     */
    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> list(
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) String metricName) {
        return ResponseEntity.ok(alertService.listRules(
                currentTenantService.requireOrganizationId(), applicationId, metricName));
    }

    @PostMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<AlertRuleResponse> create(@Valid @RequestBody CreateAlertRuleRequest request) {
        AlertRuleResponse created = alertService.createRule(
                currentTenantService.requireOrganizationId(), request);
        return ResponseEntity.created(URI.create("/api/v1/alert-rules/" + created.getId())).body(created);
    }

    /**
     * Counts logs matching the prospective filters over the requested window so a user can pick a
     * sensible threshold. Read-only and tenant-scoped; rate-limited per organization.
     */
    @PostMapping("/preview-log-count")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<LogCountPreviewResponse> previewLogCount(
            @Valid @RequestBody PreviewLogCountRequest request) {
        return ResponseEntity.ok(alertService.previewLogCount(
                currentTenantService.requireOrganizationId(), request));
    }

    @GetMapping("/{id}/revisions")
    public ResponseEntity<PageResponse<ConfigRevisionSummaryResponse>> listRevisions(
            @PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(alertService.listRevisions(
                currentTenantService.requireOrganizationId(), id, page, size));
    }

    @GetMapping("/{id}/revisions/{revisionNumber}")
    public ResponseEntity<ConfigRevisionResponse> getRevision(
            @PathVariable Long id,
            @PathVariable int revisionNumber) {
        return ResponseEntity.ok(alertService.getRevision(
                currentTenantService.requireOrganizationId(), id, revisionNumber));
    }

    @PostMapping("/{id}/revisions/{revisionNumber}/restore")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "ALERT_RULE_REVISION_RESTORED", resourceId = "#id")
    public ResponseEntity<AlertRuleResponse> restoreRevision(
            @PathVariable Long id,
            @PathVariable int revisionNumber) {
        return ResponseEntity.ok(alertService.restoreRevision(
                currentTenantService.requireOrganizationId(), id, revisionNumber));
    }

    @PutMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<AlertRuleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAlertRuleRequest request) {
        return ResponseEntity.ok(alertService.updateRule(
                currentTenantService.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        alertService.deleteRule(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
