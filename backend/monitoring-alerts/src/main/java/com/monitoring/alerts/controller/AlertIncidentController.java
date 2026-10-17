package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.AlertIncidentResponse;
import com.monitoring.alerts.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @GetMapping
    public ResponseEntity<List<AlertIncidentResponse>> list(
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(alertService.listIncidents(currentUserId(), limit));
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;

        if (principal instanceof String userId) {
            try {
                return Long.parseLong(userId);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid user ID in authentication token");
            }
        }
        throw new IllegalStateException("No authenticated user available");
    }
}
