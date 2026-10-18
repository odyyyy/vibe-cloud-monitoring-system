package com.monitoring.api.controller;

import com.monitoring.api.dto.response.WorkspaceSummaryResponse;
import com.monitoring.api.service.WorkspaceSummaryService;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Workspace summary for the Overview page.
 *
 * <p>Authentication is JWT-based; the counters are resolved for the authenticated user id,
 * which is never taken from the request.
 */
@RestController
@RequestMapping("/api/v1/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final WorkspaceSummaryService workspaceSummaryService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<WorkspaceSummaryResponse> summary() {
        return ResponseEntity.ok(workspaceSummaryService.summarize(
                currentTenantService.requireOrganizationId(), Instant.now()));
    }
}
