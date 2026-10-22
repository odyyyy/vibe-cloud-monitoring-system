package com.monitoring.retention.controller;

import com.monitoring.audit.annotation.AuditLog;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.security.RequireRole;
import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.organization.tenant.TenantContext;
import com.monitoring.retention.dto.request.UpdateRetentionPolicyRequest;
import com.monitoring.retention.dto.response.RetentionPolicyResponse;
import com.monitoring.retention.dto.response.RetentionPurgeRunResponse;
import com.monitoring.retention.service.RetentionPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Per-organization retention configuration and purge history.
 *
 * <p>All endpoints act on the active organization resolved from the request, never on an
 * identifier supplied by the client. Changing the policy requires {@code ADMIN} or
 * {@code OWNER}; reading is available to every member. Successful updates are recorded in the
 * audit trail by {@code @AuditLog}.
 */
@RestController
@RequestMapping("/api/v1/organizations/current/retention")
@RequiredArgsConstructor
public class RetentionController {

    private static final int DEFAULT_RUN_LIMIT = 20;
    private static final int MAX_RUN_LIMIT = 100;

    private final RetentionPolicyService retentionPolicyService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public RetentionPolicyResponse get() {
        return retentionPolicyService.get(currentTenantService.requireOrganizationId());
    }

    @PutMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    @AuditLog(action = "RETENTION_POLICY_UPDATED")
    public RetentionPolicyResponse update(@Valid @RequestBody UpdateRetentionPolicyRequest request) {
        TenantContext tenant = currentTenantService.requireTenant();
        return retentionPolicyService.update(tenant.organizationId(), tenant.userId(), request);
    }

    @GetMapping("/runs")
    public List<RetentionPurgeRunResponse> runs(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_RUN_LIMIT) int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_RUN_LIMIT);
        return retentionPolicyService.recentRuns(currentTenantService.requireOrganizationId(), effectiveLimit)
                .stream()
                .map(RetentionPurgeRunResponse::from)
                .toList();
    }
}
