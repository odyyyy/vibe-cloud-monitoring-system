package com.monitoring.collector.controller;

import com.monitoring.collector.dto.response.AggregatedMetricResponse;
import com.monitoring.collector.dto.request.MetricNamesRequest;
import com.monitoring.collector.dto.request.MetricsQueryRequest;
import com.monitoring.collector.service.MetricQueryService;
import com.monitoring.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Query endpoint for users. Authentication is JWT-based (see the security chains in
 * monitoring-auth / monitoring-api), so the owning application is resolved from the user
 * id in the {@link SecurityContextHolder}.
 *
 * <p>Request parameters are bound into {@link MetricsQueryRequest} from the query string;
 * validation errors result in HTTP 400 (see the global ApiExceptionHandler). Querying
 * another user's application results in HTTP 403.
 */
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricQueryController {

    private final MetricQueryService metricQueryService;
    private final CurrentTenantService currentTenantService;

    @GetMapping("/query")
    public ResponseEntity<List<AggregatedMetricResponse>> query(@Valid MetricsQueryRequest request) {
        List<AggregatedMetricResponse> response = metricQueryService.query(
                currentTenantService.requireOrganizationId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/names")
    public ResponseEntity<List<String>> names(@Valid MetricNamesRequest request) {
        return ResponseEntity.ok(metricQueryService.listMetricNames(
                currentTenantService.requireOrganizationId(), request.getApplicationId()));
    }
}
