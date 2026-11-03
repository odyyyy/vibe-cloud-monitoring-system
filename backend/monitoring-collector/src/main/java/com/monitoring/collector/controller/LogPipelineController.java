package com.monitoring.collector.controller;

import com.monitoring.collector.dto.request.CreateLogPipelineRuleRequest;
import com.monitoring.collector.dto.request.ReorderLogPipelineRulesRequest;
import com.monitoring.collector.dto.request.TestLogPipelineRequest;
import com.monitoring.collector.dto.request.UpdateLogPipelineRuleRequest;
import com.monitoring.collector.dto.response.LogPipelineRuleResponse;
import com.monitoring.collector.dto.response.LogPipelineTestResponse;
import com.monitoring.collector.entity.LogPipelineRule;
import com.monitoring.collector.service.LogPipelineService;
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

/** CRUD, reordering and simulation for tenant-scoped log pipeline rules. */
@RestController
@RequestMapping("/api/v1/log-pipelines")
@RequiredArgsConstructor
public class LogPipelineController {

    private final LogPipelineService logPipelineService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<LogPipelineRuleResponse>> list() {
        return ResponseEntity.ok(logPipelineService.list(
                currentTenantService.requireOrganizationId()));
    }

    @PostMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<LogPipelineRuleResponse> create(
            @Valid @RequestBody CreateLogPipelineRuleRequest request) {
        LogPipelineRuleResponse created = logPipelineService.create(
                currentTenantService.requireOrganizationId(), request);
        return ResponseEntity.created(URI.create("/api/v1/log-pipelines/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<LogPipelineRuleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLogPipelineRuleRequest request) {
        return ResponseEntity.ok(logPipelineService.update(
                currentTenantService.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        logPipelineService.delete(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reorder")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<List<LogPipelineRuleResponse>> reorder(
            @Valid @RequestBody ReorderLogPipelineRulesRequest request) {
        return ResponseEntity.ok(logPipelineService.reorder(
                currentTenantService.requireOrganizationId(), request));
    }

    @PostMapping("/test")
    public ResponseEntity<LogPipelineTestResponse> test(
            @Valid @RequestBody TestLogPipelineRequest request) {
        return ResponseEntity.ok(logPipelineService.test(
                currentTenantService.requireOrganizationId(), request));
    }
}
