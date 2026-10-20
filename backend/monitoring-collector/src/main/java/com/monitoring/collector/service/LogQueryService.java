package com.monitoring.collector.service;

import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.collector.dto.response.LogEventResponse;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.export.ExportData;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.apps.service.ApplicationService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads stored log events with filtering and paging.
 *
 * <p>Before querying {@code log_events} it verifies the application belongs to the current
 * user ({@link ApplicationService#getOwnedApplication}); the application id from the request
 * is never trusted on its own (tenant invariant).
 *
 * <p>All filtering, sorting and paging happen in the database. Results are ordered by event
 * time with {@code id} as a deterministic tie-breaker.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogQueryService {

    private static final char LIKE_ESCAPE = '\\';

    private final LogEventRepository logEventRepository;
    private final ApplicationService applicationService;

    public PageResponse<LogEventResponse> query(Long organizationId, LogsQueryRequest request) {
        applicationService.getOwnedApplication(request.getApplicationId(), organizationId);

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
        );

        Page<LogEvent> page = logEventRepository.findAll(toSpecification(request), pageable);

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

        Page<LogEvent> page = logEventRepository.findAll(toSpecification(request), pageable);
        List<LogEventResponse> rows = page.getContent().stream()
                .map(LogEventResponse::fromEntity)
                .toList();

        return new ExportData<>(rows, page.getTotalElements());
    }

    private Specification<LogEvent> toSpecification(LogsQueryRequest request) {
        String searchText = normalize(request.getSearchText());

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("application").get("id"), request.getApplicationId()));
            predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), request.getFrom()));
            predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), request.getTo()));

            if (request.getLevel() != null) {
                predicates.add(cb.equal(root.get("level"), request.getLevel()));
            }

            if (searchText != null) {
                String pattern = "%" + escapeLike(searchText.toLowerCase()) + "%";
                predicates.add(cb.like(cb.lower(root.get("message")), pattern, LIKE_ESCAPE));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Escapes LIKE wildcards so the search text is matched literally, not as a pattern. */
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
