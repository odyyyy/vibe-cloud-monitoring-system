package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.request.AddIncidentCommentRequest;
import com.monitoring.alerts.dto.request.UpdateIncidentNotesRequest;
import com.monitoring.alerts.dto.response.AlertIncidentResponse;
import com.monitoring.alerts.dto.response.IncidentCommentResponse;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.service.AlertService;
import com.monitoring.alerts.service.IncidentCommentService;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.security.RequireRole;
import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.organization.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Incident history and lifecycle actions for authenticated users.
 *
 * <p>The legacy {@code /api/v1/alert-incidents} path is kept as an alias so existing clients keep
 * working, while new clients use {@code /api/v1/incidents}. Incidents are still created by the
 * evaluator; users can only acknowledge, resolve and annotate them, always within their tenant.
 */
@RestController
@RequestMapping({"/api/v1/incidents", "/api/v1/alert-incidents"})
@RequiredArgsConstructor
public class AlertIncidentController {

    private final AlertService alertService;
    private final IncidentCommentService commentService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<AlertIncidentResponse>> list(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "status", required = false) IncidentStatus status) {
        return ResponseEntity.ok(alertService.listIncidents(
                currentTenantService.requireOrganizationId(), limit, status));
    }

    @PostMapping("/{id}/acknowledge")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<AlertIncidentResponse> acknowledge(@PathVariable Long id) {
        TenantContext tenant = currentTenantService.requireTenant();
        return ResponseEntity.ok(alertService.acknowledgeIncident(
                tenant.organizationId(), tenant.userId(), id));
    }

    @PostMapping("/{id}/resolve")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<AlertIncidentResponse> resolve(@PathVariable Long id) {
        TenantContext tenant = currentTenantService.requireTenant();
        return ResponseEntity.ok(alertService.resolveIncident(
                tenant.organizationId(), tenant.userId(), id));
    }

    @PutMapping("/{id}/notes")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<AlertIncidentResponse> updateNotes(
            @PathVariable Long id, @Valid @RequestBody UpdateIncidentNotesRequest request) {
        return ResponseEntity.ok(alertService.updateIncidentNotes(
                currentTenantService.requireOrganizationId(), id, request));
    }

    /**
     * Incident war-room thread. Any organization member may read it; any member may post, while
     * slash commands inside a comment require ADMIN/OWNER and are enforced by the service.
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<PageResponse<IncidentCommentResponse>> listComments(
            @PathVariable Long id,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return ResponseEntity.ok(commentService.listComments(
                currentTenantService.requireOrganizationId(), id, page, size));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<IncidentCommentResponse> addComment(
            @PathVariable Long id, @Valid @RequestBody AddIncidentCommentRequest request) {
        TenantContext tenant = currentTenantService.requireTenant();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addComment(
                        tenant.organizationId(), tenant.userId(), tenant.role(), id, request));
    }
}
