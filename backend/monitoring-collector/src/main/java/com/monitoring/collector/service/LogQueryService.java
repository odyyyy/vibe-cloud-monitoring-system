package com.monitoring.collector.service;

import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.collector.dto.response.LogEventResponse;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.export.ExportData;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.LogEventSpecifications;
import com.monitoring.apps.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Reads stored log events with filtering and paging.
 *
 * <p>Before querying {@code log_events} it verifies the application belongs to the current
 * user ({@link ApplicationService#getOwnedApplication}); the application id from the request
 * is never trusted on its own (tenant invariant).
 *
 * <p>All filtering, sorting and paging happen in the database. Results are ordered by event
 * time with {@code id} as a deterministic tie-breaker. The filter definition itself lives in
 * {@link LogEventSpecifications} so that the facet aggregation counts exactly the same rows.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogQueryService {

    private final LogEventRepository logEventRepository;
    private final LogEventSpecifications logEventSpecifications;
    private final ApplicationService applicationService;

    public PageResponse<LogEventResponse> query(Long organizationId, LogsQueryRequest request) {
        applicationService.getOwnedApplication(request.getApplicationId(), organizationId);

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
        );

        Page<LogEvent> page = logEventRepository.findAll(logEventSpecifications.matching(request), pageable);

        return PageResponse.of(page, LogEventResponse::fromEntity);
    }

    /**
     * Selects up to {@code limit} rows for a synchronous export, using the same filters,
     * ordering and tenant check as {@link #query}. The repository reports the total number of
     * matching rows, so callers can detect that the export was truncated without loading the
     * remaining rows into memory.
     */
    public ExportData<LogEventResponse> exportRows(Long organizationId, LogsQueryRequest request, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Export limit must be positive");
        }

        applicationService.getOwnedApplication(request.getApplicationId(), organizationId);

        Pageable pageable = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
        );

        Page<LogEvent> page = logEventRepository.findAll(logEventSpecifications.matching(request), pageable);
        List<LogEventResponse> rows = page.getContent().stream()
                .map(LogEventResponse::fromEntity)
                .toList();

        return new ExportData<>(rows, page.getTotalElements());
    }

    /**
     * Counts matching log events without loading any rows. Used by log-based alert evaluation and
     * by the alert-rule preview endpoint.
     *
     * <p>The application is resolved through {@link ApplicationService#getOwnedApplication}, so a
     * caller can never count another tenant's logs even by guessing an application id. The text
     * filter is a literal, case-insensitive substring; LIKE wildcards are escaped before the
     * parameterized query runs.
     */
    public long countLogs(
            Long organizationId,
            Long applicationId,
            LogLevel level,
            String searchText,
            Instant from,
            Instant to) {
        applicationService.getOwnedApplication(applicationId, organizationId);

        String normalized = normalize(searchText);
        String searchPattern = normalized == null
                ? ""
                : "%" + LogEventSpecifications.escapeLike(normalized.toLowerCase()) + "%";
        String levelName = level == null ? "" : level.name();

        return logEventRepository.countForAlert(applicationId, from, to, levelName, searchPattern);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
