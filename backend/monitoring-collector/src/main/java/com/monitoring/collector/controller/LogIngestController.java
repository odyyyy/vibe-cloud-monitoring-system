package com.monitoring.collector.controller;

import com.monitoring.collector.dto.LogIngestRequest;
import com.monitoring.collector.service.LogIngestService;
import com.monitoring.apps.entity.Application;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestion endpoint for agents (data plane).
 *
 * <p>Authentication is by API key in the {@code X-API-Key} header (see
 * {@code ApiKeyAuthFilter}); JWT is not used here. The owning application is taken from the
 * security context, never from the request body.
 */
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class LogIngestController {

    private final LogIngestService logIngestService;

    @PostMapping("/logs")
    public ResponseEntity<Void> ingestLogs(@Valid @RequestBody LogIngestRequest request) {
        Application application = currentApplication();
        logIngestService.ingest(application, request);
        return ResponseEntity.accepted().build();
    }

    private Application currentApplication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Application application) {
            return application;
        }
        throw new IllegalStateException("No authenticated application in security context");
    }
}
