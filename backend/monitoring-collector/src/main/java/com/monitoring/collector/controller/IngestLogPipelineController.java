package com.monitoring.collector.controller;

import com.monitoring.apps.entity.Application;
import com.monitoring.collector.dto.response.AgentLogPipelineRulesResponse;
import com.monitoring.collector.dto.response.LogPipelineRuleResponse;
import com.monitoring.collector.service.LogPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Rule download for agents. The application and therefore the tenant are derived exclusively
 * from the X-API-Key principal; the agent cannot request another application's rules.
 */
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestLogPipelineController {

    private final LogPipelineService logPipelineService;

    @GetMapping("/log-pipelines")
    public ResponseEntity<AgentLogPipelineRulesResponse> rules() {
        Application application = currentApplication();
        List<LogPipelineRuleResponse> rules = logPipelineService.rulesForAgent(application).stream()
                .map(logPipelineService::toResponse)
                .toList();
        return ResponseEntity.ok(new AgentLogPipelineRulesResponse(rules));
    }

    private Application currentApplication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Application application) {
            return application;
        }
        throw new IllegalStateException("No authenticated application in security context");
    }
}
