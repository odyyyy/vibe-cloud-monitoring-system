package com.monitoring.api.controller;

import com.monitoring.api.dto.response.SearchResponse;
import com.monitoring.api.service.SearchService;
import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.organization.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped resource search for the global command palette.
 *
 * <p>The organization and user are derived from the authenticated request; neither is accepted
 * from query parameters. Search is available to every organization member because the underlying
 * list endpoints are readable by viewers as well as admins.
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "types", required = false) String types,
            @RequestParam(name = "limit", defaultValue = "" + SearchService.DEFAULT_LIMIT) int limit) {
        TenantContext tenant = currentTenantService.requireTenant();
        return ResponseEntity.ok(searchService.search(
                tenant.userId(), tenant.organizationId(), query, types, limit));
    }
}
