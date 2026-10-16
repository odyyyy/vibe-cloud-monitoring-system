package com.monitoring.collector.controller;

import com.monitoring.collector.dto.LogEventResponse;
import com.monitoring.collector.dto.LogsQueryRequest;
import com.monitoring.collector.dto.PageResponse;
import com.monitoring.collector.service.LogQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @GetMapping
    public ResponseEntity<PageResponse<LogEventResponse>> query(@Valid LogsQueryRequest request) {
        return ResponseEntity.ok(logQueryService.query(currentUserId(), request));
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
