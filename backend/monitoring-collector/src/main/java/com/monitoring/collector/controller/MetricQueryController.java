package com.monitoring.collector.controller;

import com.monitoring.collector.dto.response.AggregatedMetricResponse;
import com.monitoring.collector.dto.request.MetricNamesRequest;
import com.monitoring.collector.dto.request.MetricsQueryRequest;
import com.monitoring.collector.export.ExportData;
import com.monitoring.collector.export.ExportFormat;
import com.monitoring.collector.export.ExportResponseWriter;
import com.monitoring.collector.service.MetricQueryService;
import com.monitoring.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Query endpoint for users. Authentication is JWT-based (see the security chains in
 * monitoring-auth / monitoring-api), so the owning application is resolved from the user
 * id in the {@link SecurityContextHolder}.
 *
 * <p>Request parameters are bound into {@link MetricsQueryRequest} from the query string;
 * validation errors result in HTTP 400 (see the global ApiExceptionHandler). Querying
 * another user's application results in HTTP 403.
 *
 * <p>The optional {@code format} parameter turns the response into an attachment download.
 * Without it the existing JSON array contract is preserved.
 */
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricQueryController {

    private static final List<String> EXPORT_HEADERS = List.of("timestamp", "value", "group");

    private final MetricQueryService metricQueryService;
    private final CurrentTenantService currentTenantService;
    private final ExportResponseWriter exportResponseWriter;

    @GetMapping("/query")
    public ResponseEntity<?> query(@Valid MetricsQueryRequest request,
                                   @RequestParam(value = "format", required = false) String format) {
        Long organizationId = currentTenantService.requireOrganizationId();
        ExportFormat exportFormat = ExportFormat.parseOptional(format);

        if (exportFormat == null) {
            List<AggregatedMetricResponse> response = metricQueryService.query(organizationId, request);
            return ResponseEntity.ok(response);
        }

        ExportData<AggregatedMetricResponse> data = metricQueryService.exportRows(
                organizationId, request, ExportResponseWriter.MAX_EXPORT_ROWS);

        if (exportFormat == ExportFormat.JSON) {
            return exportResponseWriter.json(data);
        }

        List<List<Object>> rows = data.rows().stream()
                .map(MetricQueryController::toCsvRow)
                .toList();
        return exportResponseWriter.csv(data, EXPORT_HEADERS, rows);
    }

    @GetMapping("/names")
    public ResponseEntity<List<String>> names(@Valid MetricNamesRequest request) {
        return ResponseEntity.ok(metricQueryService.listMetricNames(
                currentTenantService.requireOrganizationId(), request.getApplicationId()));
    }

    private static List<Object> toCsvRow(AggregatedMetricResponse point) {
        List<Object> row = new ArrayList<>(EXPORT_HEADERS.size());
        row.add(point.getTimestamp() == null ? null : point.getTimestamp().toString());
        row.add(point.getValue());
        row.add(point.getGroup());
        return row;
    }
}
