package com.monitoring.collector.service;

import com.monitoring.common.dto.PageResponse;
import com.monitoring.collector.dto.TraceDetailResponse;
import com.monitoring.collector.dto.TraceSpanResponse;
import com.monitoring.collector.dto.TraceSummaryResponse;
import com.monitoring.collector.dto.TracesQueryRequest;
import com.monitoring.collector.entity.SpanStatus;
import com.monitoring.collector.entity.TraceSpan;
import com.monitoring.collector.exception.TraceNotFoundException;
import com.monitoring.collector.repository.TraceSpanRepository;
import com.monitoring.storage.service.ApplicationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads stored trace spans: a single trace for the waterfall and a paged list of recent
 * traces with filters.
 *
 * <p>Before querying {@code trace_spans} the application is checked to belong to the current
 * user ({@link ApplicationService#getOwnedApplication}); the application id from the request
 * is never trusted on its own (tenant invariant).
 *
 * <p>The recent-traces listing aggregates whole traces (start, end, duration, span count,
 * error flag) in the database with a grouped native query, so no unbounded result set is
 * loaded into memory.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TraceQueryService {

    private static final String FROM_AND_BASE_FILTERS =
            " FROM trace_spans s"
                    + " WHERE s.application_id = :applicationId"
                    + " AND s.start_time >= :from AND s.start_time <= :to";

    private final TraceSpanRepository traceSpanRepository;
    private final ApplicationService applicationService;
    private final EntityManager entityManager;

    public TraceDetailResponse getTrace(Long organizationId, String traceId) {
        List<TraceSpan> spans = traceSpanRepository.findByTraceIdOrderByStartTimeAscIdAsc(traceId);
        if (spans.isEmpty()) {
            throw new TraceNotFoundException(traceId);
        }
        verifyOwnership(organizationId, spans);

        Instant start = spans.get(0).getStartTime();
        Instant end = spans.get(0).getEndTime();
        boolean hasError = false;
        for (TraceSpan span : spans) {
            if (span.getStartTime().isBefore(start)) {
                start = span.getStartTime();
            }
            if (span.getEndTime().isAfter(end)) {
                end = span.getEndTime();
            }
            if (span.getStatus() == SpanStatus.ERROR) {
                hasError = true;
            }
        }

        return TraceDetailResponse.builder()
                .traceId(traceId)
                .applicationId(spans.get(0).getApplication().getId())
                .startTime(start)
                .durationMs(Math.max(0, Duration.between(start, end).toMillis()))
                .hasError(hasError)
                .spans(spans.stream().map(TraceSpanResponse::fromEntity).toList())
                .build();
    }

    public PageResponse<TraceSummaryResponse> list(Long organizationId, TracesQueryRequest request) {
        applicationService.getOwnedApplication(request.getApplicationId(), organizationId);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("applicationId", request.getApplicationId());
        params.put("from", request.getFrom());
        params.put("to", request.getTo());

        StringBuilder where = new StringBuilder(FROM_AND_BASE_FILTERS);
        String serviceName = normalize(request.getServiceName());
        if (serviceName != null) {
            where.append(" AND s.service_name = :serviceName");
            params.put("serviceName", serviceName);
        }

        String groupedSelect = "SELECT s.trace_id AS trace_id,"
                + " MIN(s.start_time) AS min_start,"
                + " MAX(s.end_time) AS max_end,"
                + " TIMESTAMPDIFF(MICROSECOND, MIN(s.start_time), MAX(s.end_time)) AS duration_micros,"
                + " COUNT(*) AS span_count,"
                + " MAX(CASE WHEN s.status = 'ERROR' THEN 1 ELSE 0 END) AS has_error"
                + " FROM trace_spans s" + where
                + " GROUP BY s.trace_id"
                + buildHaving(request, params);

        long total = countTraces(groupedSelect, params);
        if (total == 0) {
            return new PageResponse<>(List.of(), 0, 0, request.getPage(), request.getSize());
        }

        Query dataQuery = entityManager.createNativeQuery(
                groupedSelect + " ORDER BY min_start DESC LIMIT :limit OFFSET :offset");
        bind(dataQuery, params);
        dataQuery.setParameter("limit", request.getSize());
        dataQuery.setParameter("offset", (long) request.getPage() * request.getSize());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        List<TraceSummaryResponse> content = rows.stream().map(this::toSummary).toList();

        int totalPages = (int) ((total + request.getSize() - 1) / request.getSize());
        return new PageResponse<>(content, total, totalPages, request.getPage(), request.getSize());
    }

    private void verifyOwnership(Long organizationId, List<TraceSpan> spans) {
        Set<Long> applicationIds = spans.stream()
                .map(span -> span.getApplication().getId())
                .collect(Collectors.toSet());
        for (Long applicationId : applicationIds) {
            applicationService.getOwnedApplication(applicationId, organizationId);
        }
    }

    private String buildHaving(TracesQueryRequest request, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();
        if (request.getMinDurationMs() != null) {
            conditions.add("TIMESTAMPDIFF(MICROSECOND, MIN(s.start_time), MAX(s.end_time)) >= :minDurationMicros");
            params.put("minDurationMicros", request.getMinDurationMs() * 1000L);
        }
        if (request.isHasErrors()) {
            conditions.add("MAX(CASE WHEN s.status = 'ERROR' THEN 1 ELSE 0 END) = 1");
        }
        return conditions.isEmpty() ? "" : " HAVING " + String.join(" AND ", conditions);
    }

    private long countTraces(String groupedSelect, Map<String, Object> params) {
        Query countQuery = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM (" + groupedSelect + ") t");
        bind(countQuery, params);
        Number result = (Number) countQuery.getSingleResult();
        return result == null ? 0 : result.longValue();
    }

    private void bind(Query query, Map<String, Object> params) {
        params.forEach(query::setParameter);
    }

    private TraceSummaryResponse toSummary(Object[] row) {
        long durationMicros = row[3] == null ? 0 : ((Number) row[3]).longValue();
        long spanCount = row[4] == null ? 0 : ((Number) row[4]).longValue();
        int hasError = row[5] == null ? 0 : ((Number) row[5]).intValue();
        return TraceSummaryResponse.builder()
                .traceId((String) row[0])
                .startTime(toInstant(row[1]))
                .durationMs(durationMicros / 1000)
                .spanCount(spanCount)
                .hasError(hasError == 1)
                .build();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        throw new IllegalStateException("Unexpected temporal type: " + value.getClass());
    }
}
