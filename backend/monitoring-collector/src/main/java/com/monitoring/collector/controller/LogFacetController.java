package com.monitoring.collector.controller;

import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.collector.dto.response.LogFacetsResponse;
import com.monitoring.collector.exception.InvalidLogFacetRequestException;
import com.monitoring.collector.facet.LogFacetField;
import com.monitoring.collector.facet.LogFacetFields;
import com.monitoring.collector.service.LogFacetService;
import com.monitoring.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Facet endpoint for the log explorer.
 *
 * <p>It is a separate endpoint on purpose: the paginated result list is never slowed down by the
 * aggregation, the client can load both in parallel, and the export and pagination logic stay
 * untouched.
 *
 * <p>The endpoint accepts the same filter and time-range parameters as
 * {@code GET /api/v1/logs}, plus {@code fields} (the whitelisted fields to aggregate) and
 * {@code limit} (the number of values per field).
 *
 * <p>Authentication is JWT-based; the owning application is resolved from the organization of the
 * authenticated user, so a caller can never read another tenant's logs.
 */
@RestController
@RequestMapping("/api/v1/logs/facets")
@RequiredArgsConstructor
public class LogFacetController {

    private final LogFacetService logFacetService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<LogFacetsResponse> facets(
            @Valid LogsQueryRequest request,
            @RequestParam(name = "fields", required = false) String fields,
            @RequestParam(name = "limit", required = false) Integer limit) {
        Long organizationId = currentTenantService.requireOrganizationId();

        int effectiveLimit = limit == null ? LogFacetService.DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > LogFacetService.MAX_LIMIT) {
            throw new InvalidLogFacetRequestException(
                    "limit must be between 1 and " + LogFacetService.MAX_LIMIT);
        }

        List<LogFacetField> requested = LogFacetFields.parse(fields, LogFacetService.MAX_FIELDS);

        return ResponseEntity.ok(logFacetService.facets(organizationId, request, requested, effectiveLimit));
    }
}
