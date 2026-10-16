package com.monitoring.api.controller;

import com.monitoring.storage.dto.ApplicationCreateRequest;
import com.monitoring.storage.dto.ApplicationResponse;
import com.monitoring.storage.dto.ApplicationUpdateRequest;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.service.ApplicationService;
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
    public ResponseEntity<ApplicationResponse> createApplication(
            @Valid @RequestBody ApplicationCreateRequest request) {
        Application application = applicationService.createApplication(request, currentUserId());
        URI location = URI.create("/api/applications/" + application.getId());
        return ResponseEntity.created(location).body(ApplicationResponse.fromEntity(application));
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
    public ResponseEntity<Void> deleteApplication(@PathVariable Long id) {
        applicationService.deleteApplication(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/regenerate-api-key")
    public ResponseEntity<ApplicationResponse> regenerateApiKey(@PathVariable Long id) {
        Application application = applicationService.regenerateApiKey(id, currentUserId());
        return ResponseEntity.ok(ApplicationResponse.fromEntity(application));
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
