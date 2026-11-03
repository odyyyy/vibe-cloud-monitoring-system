package com.monitoring.collector.controller;

import com.monitoring.collector.dto.request.AssignAgentConfigTemplateRequest;
import com.monitoring.collector.dto.request.CreateAgentConfigTemplateRequest;
import com.monitoring.collector.dto.request.UpdateAgentConfigTemplateRequest;
import com.monitoring.collector.dto.response.AgentConfigTemplateResponse;
import com.monitoring.collector.dto.response.ApplicationAgentConfigResponse;
import com.monitoring.collector.service.AgentConfigService;
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

/** JWT-authenticated CRUD for centralized agent configuration templates. */
@RestController
@RequestMapping("/api/v1/agent-configs")
@RequiredArgsConstructor
public class AgentConfigController {

    private final AgentConfigService agentConfigService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<AgentConfigTemplateResponse>> list() {
        return ResponseEntity.ok(agentConfigService.list(
                currentTenantService.requireOrganizationId()));
    }

    @PostMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<AgentConfigTemplateResponse> create(
            @Valid @RequestBody CreateAgentConfigTemplateRequest request) {
        AgentConfigTemplateResponse created = agentConfigService.create(
                currentTenantService.requireOrganizationId(), request);
        return ResponseEntity.created(URI.create("/api/v1/agent-configs/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<AgentConfigTemplateResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAgentConfigTemplateRequest request) {
        return ResponseEntity.ok(agentConfigService.update(
                currentTenantService.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        agentConfigService.delete(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<ApplicationAgentConfigResponse> applicationConfig(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(agentConfigService.applicationConfig(
                currentTenantService.requireOrganizationId(), applicationId));
    }

    @PutMapping("/applications/{applicationId}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<ApplicationAgentConfigResponse> assign(
            @PathVariable Long applicationId,
            @Valid @RequestBody AssignAgentConfigTemplateRequest request) {
        return ResponseEntity.ok(agentConfigService.assign(
                currentTenantService.requireOrganizationId(), applicationId, request));
    }
}
