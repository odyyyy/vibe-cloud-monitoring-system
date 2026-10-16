package com.monitoring.collector.controller;

import com.monitoring.collector.dto.AggregatedMetricResponse;
import com.monitoring.collector.dto.MetricsQueryRequest;
import com.monitoring.collector.service.MetricQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @GetMapping("/query")
    public ResponseEntity<List<AggregatedMetricResponse>> query(@Valid MetricsQueryRequest request) {
        List<AggregatedMetricResponse> response = metricQueryService.query(currentUserId(), request);
        return ResponseEntity.ok(response);
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
