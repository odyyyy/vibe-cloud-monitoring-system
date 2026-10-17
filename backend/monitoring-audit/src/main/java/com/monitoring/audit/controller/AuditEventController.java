package com.monitoring.audit.controller;

import com.monitoring.audit.dto.AuditEventQuery;
import com.monitoring.audit.dto.AuditEventResponse;
import com.monitoring.audit.dto.PageResponse;
import com.monitoring.audit.service.AuditEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to the audit trail. Events are written by {@code AuditLogAspect}; there is
 * deliberately no create, update or delete endpoint, so the trail cannot be modified through
 * the API.
 *
 * <p>The tenant scope (the current user id) is resolved from the JWT principal, never from the
 * request, and enforced in {@link AuditEventService}.
 */
@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
public class AuditEventController {

    private final AuditEventService auditEventService;

    @GetMapping
    public ResponseEntity<PageResponse<AuditEventResponse>> list(@Valid AuditEventQuery query) {
        return ResponseEntity.ok(auditEventService.list(currentUserId(), query));
    }

    /** Returns a single event, or 404 when it does not exist or belongs to another user. */
    @GetMapping("/{id}")
    public ResponseEntity<AuditEventResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.of(auditEventService.find(currentUserId(), id));
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
