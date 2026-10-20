package com.monitoring.collector.controller;

import com.monitoring.collector.dto.response.LogEventResponse;
import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.collector.export.ExportData;
import com.monitoring.collector.export.ExportFormat;
import com.monitoring.collector.export.ExportResponseWriter;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.collector.service.LogQueryService;
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
 * Query endpoint for users (control plane). Authentication is JWT-based; the owning
 * application is resolved from the user id in the {@link SecurityContextHolder}.
 *
 * <p>Query-string parameters are bound into {@link LogsQueryRequest}; validation failures
 * become HTTP 400 and querying another user's application becomes HTTP 403 (see the global
 * {@code ApiExceptionHandler}).
 *
 * <p>The optional {@code format} parameter turns the response into an attachment download.
 * Without it the existing paginated JSON contract is preserved.
 */
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogQueryController {

    private static final List<String> EXPORT_HEADERS =
            List.of("id", "applicationId", "timestamp", "level", "message", "context");

    private final LogQueryService logQueryService;
    private final CurrentTenantService currentTenantService;
    private final ExportResponseWriter exportResponseWriter;

    @GetMapping
    public ResponseEntity<?> query(@Valid LogsQueryRequest request,
                                   @RequestParam(value = "format", required = false) String format) {
        Long organizationId = currentTenantService.requireOrganizationId();
        ExportFormat exportFormat = ExportFormat.parseOptional(format);

        if (exportFormat == null) {
            PageResponse<LogEventResponse> page = logQueryService.query(organizationId, request);
            return ResponseEntity.ok(page);
        }

        ExportData<LogEventResponse> data = logQueryService.exportRows(
                organizationId, request, ExportResponseWriter.MAX_EXPORT_ROWS);

        if (exportFormat == ExportFormat.JSON) {
            return exportResponseWriter.json(data);
        }

        List<List<Object>> rows = data.rows().stream()
                .map(LogQueryController::toCsvRow)
                .toList();
        return exportResponseWriter.csv(data, EXPORT_HEADERS, rows);
    }

    private static List<Object> toCsvRow(LogEventResponse log) {
        List<Object> row = new ArrayList<>(EXPORT_HEADERS.size());
        row.add(log.getId());
        row.add(log.getApplicationId());
        row.add(log.getTimestamp() == null ? null : log.getTimestamp().toString());
        row.add(log.getLevel() == null ? null : log.getLevel().name());
        row.add(log.getMessage());
        row.add(log.getContext());
        return row;
    }
}
