package com.monitoring.collector.controller;

import com.monitoring.apps.entity.Application;
import com.monitoring.collector.dto.request.AgentConfigStatusRequest;
import com.monitoring.collector.dto.response.ActiveAgentConfigResponse;
import com.monitoring.collector.service.AgentConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent-facing configuration endpoints. Authentication is exclusively the {@code X-API-Key}
 * header; the application, and therefore the tenant, is always taken from the principal.
 */
@RestController
@RequestMapping("/api/v1/agent-configs")
@RequiredArgsConstructor
public class AgentConfigAgentController {

    private final AgentConfigService agentConfigService;

    @GetMapping("/active")
    public ResponseEntity<ActiveAgentConfigResponse> active() {
        return ResponseEntity.ok(agentConfigService.activeConfig(currentApplication()));
    }

    @PostMapping("/status")
    public ResponseEntity<Void> reportStatus(
            @Valid @RequestBody AgentConfigStatusRequest request) {
        agentConfigService.reportStatus(currentApplication(), request);
        return ResponseEntity.noContent().build();
    }

    private Application currentApplication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Application application) {
            return application;
        }
        throw new IllegalStateException("No authenticated application in security context");
    }
}
