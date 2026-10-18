package com.monitoring.collector.controller;

import com.monitoring.collector.dto.CreateSavedLogViewRequest;
import com.monitoring.collector.dto.SavedLogViewResponse;
import com.monitoring.collector.dto.UpdateSavedLogViewRequest;
import com.monitoring.collector.service.SavedLogViewService;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User-facing CRUD for saved log views. Authentication is JWT-based; the owner is derived from the
 * user id in the {@link SecurityContextHolder}, never from the request body. Validation failures
 * become HTTP 400, another owner's view HTTP 403, a missing view HTTP 404 and the per-user quota
 * HTTP 409.
 */
@RestController
@RequestMapping("/api/v1/saved-log-views")
@RequiredArgsConstructor
public class SavedLogViewController {

    private final SavedLogViewService savedLogViewService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<SavedLogViewResponse>> list() {
        return ResponseEntity.ok(savedLogViewService.list(currentTenantService.requireOrganizationId()));
    }

    @PostMapping
    public ResponseEntity<SavedLogViewResponse> create(@Valid @RequestBody CreateSavedLogViewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedLogViewService.create(currentTenantService.requireOrganizationId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavedLogViewResponse> rename(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSavedLogViewRequest request) {
        return ResponseEntity.ok(savedLogViewService.rename(
                currentTenantService.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        savedLogViewService.delete(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
