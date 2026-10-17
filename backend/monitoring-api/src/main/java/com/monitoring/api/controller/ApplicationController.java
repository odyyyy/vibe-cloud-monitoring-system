package com.monitoring.api.controller;

import com.monitoring.storage.dto.ApplicationCreateRequest;
import com.monitoring.storage.dto.ApplicationResponse;
import com.monitoring.storage.dto.ApplicationUpdateRequest;
import com.monitoring.storage.dto.ApplicationWithKeyResponse;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.service.ApplicationService;
import com.monitoring.audit.annotation.AuditLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> listApplications() {
        List<ApplicationResponse> response = applicationService.findByOwnerId(currentUserId())
                .stream()
                .map(ApplicationResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApplicationWithKeyResponse> createApplication(
            @Valid @RequestBody ApplicationCreateRequest request) {
        ApplicationWithKeyResponse application = applicationService.createApplication(request, currentUserId());
        URI location = URI.create("/api/applications/" + application.id());
        return ResponseEntity.created(location).body(application);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable Long id) {
        Application application = applicationService.getOwnedApplication(id, currentUserId());
        return ResponseEntity.ok(ApplicationResponse.fromEntity(application));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> updateApplication(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationUpdateRequest request) {
        Application application = applicationService.updateApplication(id, request.getName(), currentUserId());
        return ResponseEntity.ok(ApplicationResponse.fromEntity(application));
    }

    @DeleteMapping("/{id}")
    @AuditLog(action = "APPLICATION_DELETED", resourceId = "#id")
    public ResponseEntity<Void> deleteApplication(@PathVariable Long id) {
        applicationService.deleteApplication(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Rotates the agent API key. The new plaintext key is returned once and is not stored,
     * so it cannot be retrieved again.
     */
    @PostMapping("/{id}/regenerate-api-key")
    @AuditLog(action = "API_KEY_REGENERATED", resourceId = "#id")
    public ResponseEntity<ApplicationWithKeyResponse> regenerateApiKey(@PathVariable Long id) {
        return ResponseEntity.ok(applicationService.regenerateApiKey(id, currentUserId()));
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
