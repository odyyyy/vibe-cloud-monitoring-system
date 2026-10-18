package com.monitoring.api.controller;

import com.monitoring.apps.dto.AgentNodeResponse;
import com.monitoring.apps.service.AgentService;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of the agents reporting for an application.
 *
 * <p>The listing is tenant-scoped: ownership of the application is verified in the service
 * before any node is returned, so an application id from the path is never trusted on its own.
 */
@RestController
@RequestMapping("/api/applications/{applicationId}/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<AgentNodeResponse>> listAgents(@PathVariable Long applicationId) {
        return ResponseEntity.ok(agentService.listForApplication(
                applicationId, currentTenantService.requireOrganizationId()));
    }
}
