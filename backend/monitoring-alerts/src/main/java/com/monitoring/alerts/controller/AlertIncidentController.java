package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.AlertIncidentResponse;
import com.monitoring.alerts.service.AlertService;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only incident history for authenticated users, newest first. Incidents are created and
 * resolved by the evaluator; they are never written through the API.
 */
@RestController
@RequestMapping("/api/v1/alert-incidents")
@RequiredArgsConstructor
public class AlertIncidentController {

    private final AlertService alertService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<AlertIncidentResponse>> list(
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(alertService.listIncidents(
                currentTenantService.requireOrganizationId(), limit));
    }
}
