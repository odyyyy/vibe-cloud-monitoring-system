package com.monitoring.api.controller;

import com.monitoring.api.dto.WorkspaceSummaryResponse;
import com.monitoring.api.service.WorkspaceSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Workspace summary for the Overview page.
 *
 * <p>Authentication is JWT-based; the counters are resolved for the authenticated user id,
 * which is never taken from the request.
 */
@RestController
@RequestMapping("/api/v1/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final WorkspaceSummaryService workspaceSummaryService;

    @GetMapping
    public ResponseEntity<WorkspaceSummaryResponse> summary() {
        return ResponseEntity.ok(workspaceSummaryService.summarize(currentUserId(), Instant.now()));
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
