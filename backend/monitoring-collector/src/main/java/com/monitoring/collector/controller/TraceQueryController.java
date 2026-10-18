package com.monitoring.collector.controller;

import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.collector.dto.response.TraceDetailResponse;
import com.monitoring.collector.dto.response.TraceSummaryResponse;
import com.monitoring.collector.dto.request.TracesQueryRequest;
import com.monitoring.collector.service.TraceQueryService;
import com.monitoring.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Query endpoints for users (control plane). Authentication is JWT-based; the owning
 * application is resolved from the user id in the {@link SecurityContextHolder}.
 *
 * <p>Unknown traces become HTTP 404 and querying another user's application becomes HTTP 403
 * (see the global {@code ApiExceptionHandler}).
 */
@RestController
@RequestMapping("/api/v1/traces")
@RequiredArgsConstructor
public class TraceQueryController {

    private final TraceQueryService traceQueryService;
    private final CurrentTenantService currentTenantService;

    @GetMapping("/{traceId}")
    public ResponseEntity<TraceDetailResponse> getTrace(@PathVariable String traceId) {
        return ResponseEntity.ok(traceQueryService.getTrace(
                currentTenantService.requireOrganizationId(), traceId));
    }

    @GetMapping
    public ResponseEntity<PageResponse<TraceSummaryResponse>> list(@Valid TracesQueryRequest request) {
        return ResponseEntity.ok(traceQueryService.list(currentTenantService.requireOrganizationId(), request));
    }
}
