package com.monitoring.collector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.monitoring.collector.config.LogFacetProperties;
import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.collector.dto.response.LogFacetValueResponse;
import com.monitoring.collector.dto.response.LogFacetsResponse;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.exception.LogFacetRateLimitedException;
import com.monitoring.collector.exception.LogFacetTimeoutException;
import com.monitoring.collector.facet.LogContextValue;
import com.monitoring.collector.facet.LogFacetField;
import com.monitoring.collector.repository.LogEventSpecifications;
import com.monitoring.apps.service.ApplicationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates the Top-N values of the whitelisted log fields for the current search context.
 *
 * <p>The facet counts always describe exactly the row set the log explorer shows: the very same
 * filter definition ({@link LogEventSpecifications}) is used for the result list and for the
 * aggregation, including the facet filters themselves.
 *
 * <p>Two storage locations are handled differently:
 * <ul>
 *   <li>{@code level} is a real column, so it is aggregated by the database with a
 *       {@code GROUP BY} over the whole matching row set — exact and index-friendly.</li>
 *   <li>{@code host}, {@code service} and {@code k8s.namespace} live inside the JSON
 *       {@code context} document and cannot be read with an index. Their counts are computed over
 *       the most recent {@link LogFacetProperties#getMaxScanRows()} matching rows; when that cap
 *       is reached the response is marked as {@code approximate}.</li>
 * </ul>
 *
 * <p>Every query runs with a statement timeout, and the endpoint is rate limited per
 * organization, because the aggregation is more expensive than a normal log query.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogFacetService {

    /** Default number of values per field. */
    public static final int DEFAULT_LIMIT = 5;

    /** Maximum number of values per field. */
    public static final int MAX_LIMIT = 10;

    /** Maximum number of fields that may be requested in one call. */
    public static final int MAX_FIELDS = 4;

    private static final String TIMEOUT_HINT = "jakarta.persistence.query.timeout";

    private final LogEventSpecifications logEventSpecifications;
    private final ApplicationService applicationService;
    private final LogFacetRateLimiter rateLimiter;
    private final LogFacetProperties properties;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    /**
     * Computes the Top-N values of the requested fields.
     *
     * @param organizationId the tenant the caller belongs to
     * @param request        the same filters the log search uses
     * @param fields         the whitelisted fields to aggregate
     * @param limit          the number of values per field
     */
    public LogFacetsResponse facets(Long organizationId,
                                    LogsQueryRequest request,
                                    List<LogFacetField> fields,
                                    int limit) {
        applicationService.getOwnedApplication(request.getApplicationId(), organizationId);

        long retryAfter = rateLimiter.tryAcquire(organizationId, Instant.now());
        if (retryAfter > 0) {
            throw new LogFacetRateLimitedException(retryAfter);
        }

        Specification<LogEvent> specification = logEventSpecifications.matching(request);
        Map<String, List<LogFacetValueResponse>> facets = new LinkedHashMap<>();
        boolean approximate = false;

        List<LogFacetField> columnFields = fields.stream().filter(field -> !field.isContextField()).toList();
        List<LogFacetField> contextFields = fields.stream().filter(LogFacetField::isContextField).toList();

        for (LogFacetField field : columnFields) {
            facets.put(field.fieldName(), aggregateColumn(specification, limit));
        }

        if (!contextFields.isEmpty()) {
            ContextScan scan = scanContexts(specification);
            approximate = scan.truncated();
            for (LogFacetField field : contextFields) {
                facets.put(field.fieldName(), aggregateContext(field, scan.contexts(), limit));
            }
        }

        return new LogFacetsResponse(facets, approximate, properties.getMaxScanRows());
    }

    /**
     * Aggregates a real column with {@code GROUP BY ... ORDER BY COUNT(*) DESC} over the whole
     * matching row set.
     */
    private List<LogFacetValueResponse> aggregateColumn(Specification<LogEvent> specification, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<LogEvent> root = query.from(LogEvent.class);

        query.multiselect(root.get("level"), cb.count(root.get("id")));
        query.where(specification.toPredicate(root, query, cb));
        query.groupBy(root.get("level"));
        query.orderBy(cb.desc(cb.count(root.get("id"))));

        List<LogFacetValueResponse> values = new ArrayList<>();
        for (Tuple row : execute(query, limit)) {
            Object level = row.get(0);
            if (level instanceof LogLevel logLevel) {
                values.add(new LogFacetValueResponse(logLevel.name(), ((Number) row.get(1)).longValue()));
            }
        }
        return values;
    }

    /**
     * Reads the context documents of the most recent matching rows.
     *
     * <p>The scan is bounded: at most {@link LogFacetProperties#getMaxScanRows()} rows are read,
     * and one extra row is fetched to detect whether the cap was reached.
     */
    private ContextScan scanContexts(Specification<LogEvent> specification) {
        int cap = Math.max(1, properties.getMaxScanRows());

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = cb.createQuery(String.class);
        Root<LogEvent> root = query.from(LogEvent.class);

        query.select(root.get("context"));
        query.where(specification.toPredicate(root, query, cb));
        query.orderBy(cb.desc(root.get("timestamp")), cb.desc(root.get("id")));

        List<String> rows = execute(query, cap + 1);
        boolean truncated = rows.size() > cap;
        return new ContextScan(truncated ? List.copyOf(rows.subList(0, cap)) : rows, truncated);
    }

    /**
     * Counts the values of one JSON context field over the scanned documents.
     *
     * <p>Rows without a usable value (missing key, empty value, malformed document) are skipped.
     */
    private List<LogFacetValueResponse> aggregateContext(LogFacetField field,
                                                         List<String> contexts,
                                                         int limit) {
        ObjectReader reader = objectMapper.reader();
        Map<String, Long> counts = new HashMap<>();

        for (String context : contexts) {
            String value = LogContextValue.extract(reader, context, field.contextKey());
            if (value == null || value.isBlank()) {
                continue;
            }
            counts.merge(value, 1L, Long::sum);
        }

        Comparator<Map.Entry<String, Long>> byCountDescending =
                Comparator.comparing(Map.Entry<String, Long>::getValue).reversed()
                        .thenComparing(Map.Entry.comparingByKey());

        return counts.entrySet().stream()
                .sorted(byCountDescending)
                .limit(limit)
                .map(entry -> new LogFacetValueResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Runs an aggregation query with the configured statement timeout.
     *
     * <p>A timeout is reported as {@link LogFacetTimeoutException} so the caller can answer with
     * HTTP 503; the log result list stays available.
     */
    private <T> List<T> execute(CriteriaQuery<T> query, int limit) {
        TypedQuery<T> typed = entityManager.createQuery(query);
        typed.setMaxResults(limit);
        typed.setHint(TIMEOUT_HINT, properties.getTimeoutMs());
        try {
            return typed.getResultList();
        } catch (PersistenceException e) {
            if (isTimeout(e)) {
                throw new LogFacetTimeoutException();
            }
            throw e;
        }
    }

    /** True when the failure was caused by the statement timeout. */
    private static boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof jakarta.persistence.QueryTimeoutException
                    || current instanceof org.springframework.dao.QueryTimeoutException
                    || current instanceof SQLTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** The scanned context documents and whether the scan limit was reached. */
    private record ContextScan(List<String> contexts, boolean truncated) {
    }
}
