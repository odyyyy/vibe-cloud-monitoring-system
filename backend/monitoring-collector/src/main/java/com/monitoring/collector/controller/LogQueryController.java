package com.monitoring.collector.controller;

import com.monitoring.collector.dto.response.LogEventResponse;
import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.common.dto.PageResponse;
import com.monitoring.collector.service.LogQueryService;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Query endpoint for users (control plane). Authentication is JWT-based; the owning
 * application is resolved from the user id in the {@link SecurityContextHolder}.
 *
 * <p>Query-string parameters are bound into {@link LogsQueryRequest}; validation failures
 * become HTTP 400 and querying another user's application becomes HTTP 403 (see the global
 * {@code ApiExceptionHandler}).
 */
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogQueryController {

    private final LogQueryService logQueryService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<PageResponse<LogEventResponse>> query(@Valid LogsQueryRequest request) {
        return ResponseEntity.ok(logQueryService.query(currentTenantService.requireOrganizationId(), request));
    }
}
