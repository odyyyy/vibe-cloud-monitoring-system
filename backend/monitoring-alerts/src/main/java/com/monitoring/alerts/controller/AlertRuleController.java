package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.AlertRuleResponse;
import com.monitoring.alerts.dto.CreateAlertRuleRequest;
import com.monitoring.alerts.dto.UpdateAlertRuleRequest;
import com.monitoring.alerts.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

/**
 * Alert rule CRUD for authenticated users. The tenant scope (owner) is resolved from the JWT
 * principal, never from the request, and enforced in {@link AlertService}.
 */
@RestController
@RequestMapping("/api/v1/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> list() {
        return ResponseEntity.ok(alertService.listRules(currentUserId()));
    }

    @PostMapping
    public ResponseEntity<AlertRuleResponse> create(@Valid @RequestBody CreateAlertRuleRequest request) {
        AlertRuleResponse created = alertService.createRule(currentUserId(), request);
        return ResponseEntity.created(URI.create("/api/v1/alert-rules/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAlertRuleRequest request) {
        return ResponseEntity.ok(alertService.updateRule(currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        alertService.deleteRule(currentUserId(), id);
        return ResponseEntity.noContent().build();
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
