package com.monitoring.api.service;

import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.api.dto.response.SearchResponse;
import com.monitoring.api.dto.response.SearchResultResponse;
import com.monitoring.api.exception.InvalidSearchRequestException;
import com.monitoring.api.exception.SearchRateLimitedException;
import com.monitoring.api.exception.SearchTimeoutException;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.collector.repository.SavedLogViewRepository;
import com.monitoring.dashboards.repository.DashboardRepository;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Tenant-scoped resource search behind the global command palette.
 *
 * <p>Every resource type is queried separately with a bounded page size and a 2-second statement
 * timeout. Queries match display names only and always include the organization id derived from
 * the authenticated membership; no client-supplied tenant identifier is trusted. System
 * dashboards are excluded because they are only reachable through Platform Health.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_QUERY_LENGTH = 64;
    public static final int DEFAULT_LIMIT = 5;
    public static final int MAX_LIMIT = 10;

    private final ApplicationRepository applicationRepository;
    private final DashboardRepository dashboardRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final HealthCheckConfigRepository healthCheckConfigRepository;
    private final SavedLogViewRepository savedLogViewRepository;
    private final AlertIncidentRepository alertIncidentRepository;
    private final SearchRateLimiter rateLimiter;

    public SearchResponse search(Long userId,
                                 Long organizationId,
                                 String rawQuery,
                                 String rawTypes,
                                 Integer rawLimit) {
        String query = normalizeQuery(rawQuery);
        List<SearchResourceType> types = parseTypes(rawTypes);
        int limit = normalizeLimit(rawLimit);

        long retryAfter = rateLimiter.tryAcquire(userId, Instant.now());
        if (retryAfter > 0) {
            throw new SearchRateLimitedException(retryAfter);
        }

        String needle = query.toLowerCase(Locale.ROOT);
        Pageable pageable = PageRequest.of(0, limit);
        List<SearchResultResponse> results = new ArrayList<>();

        for (SearchResourceType type : types) {
            results.addAll(searchType(type, organizationId, needle, pageable));
        }

        return new SearchResponse(List.copyOf(results));
    }

    private List<SearchResultResponse> searchType(SearchResourceType type,
                                                   Long organizationId,
                                                   String needle,
                                                   Pageable pageable) {
        return switch (type) {
            case APPLICATIONS -> execute(() ->
                    applicationRepository.searchByName(organizationId, needle, pageable))
                    .stream()
                    .map(application -> result(
                            SearchResourceType.APPLICATIONS,
                            application.getId(),
                            application.getName(),
                            "Application"))
                    .toList();
            case DASHBOARDS -> execute(() ->
                    dashboardRepository.searchByName(organizationId, needle, pageable))
                    .stream()
                    .map(dashboard -> result(
                            SearchResourceType.DASHBOARDS,
                            dashboard.getId(),
                            dashboard.getName(),
                            "Dashboard"))
                    .toList();
            case ALERT_RULES -> execute(() ->
                    alertRuleRepository.searchByName(organizationId, needle, pageable))
                    .stream()
                    .map(rule -> result(
                            SearchResourceType.ALERT_RULES,
                            rule.getId(),
                            rule.getName(),
                            "Alert rule"))
                    .toList();
            case HEALTH_CHECKS -> execute(() ->
                    healthCheckConfigRepository.searchByName(organizationId, needle, pageable))
                    .stream()
                    .map(check -> result(
                            SearchResourceType.HEALTH_CHECKS,
                            check.getId(),
                            check.getName(),
                            "Health check"))
                    .toList();
            case SAVED_VIEWS -> execute(() ->
                    savedLogViewRepository.searchByName(organizationId, needle, pageable))
                    .stream()
                    .map(view -> result(
                            SearchResourceType.SAVED_VIEWS,
                            view.getId(),
                            view.getName(),
                            "Saved log view"))
                    .toList();
            case INCIDENTS -> execute(() ->
                    alertIncidentRepository.searchByRuleName(organizationId, needle, pageable))
                    .stream()
                    .map(this::incidentResult)
                    .toList();
        };
    }

    private SearchResultResponse incidentResult(AlertIncident incident) {
        return result(
                SearchResourceType.INCIDENTS,
                incident.getId(),
                incident.getRule().getName(),
                "Incident · " + incident.getStatus().name());
    }

    private SearchResultResponse result(SearchResourceType type, Long id, String title, String subtitle) {
        return new SearchResultResponse(type.wireName(), id, title, subtitle);
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null) {
            throw new InvalidSearchRequestException("Search query is required");
        }
        String query = rawQuery.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            throw new InvalidSearchRequestException(
                    "Search query must be at least " + MIN_QUERY_LENGTH + " characters");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidSearchRequestException(
                    "Search query must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        return query;
    }

    private List<SearchResourceType> parseTypes(String rawTypes) {
        if (rawTypes == null || rawTypes.isBlank()) {
            return List.of(SearchResourceType.values());
        }
        Set<SearchResourceType> types = new LinkedHashSet<>();
        for (String part : rawTypes.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            String candidate = part.trim();
            SearchResourceType type = SearchResourceType.fromWireName(candidate)
                    .orElseThrow(() -> new InvalidSearchRequestException(
                            "Unknown search resource type '" + candidate + "'"));
            types.add(type);
        }
        return types.isEmpty() ? List.of(SearchResourceType.values()) : List.copyOf(types);
    }

    private int normalizeLimit(Integer rawLimit) {
        if (rawLimit == null) {
            return DEFAULT_LIMIT;
        }
        if (rawLimit < 1 || rawLimit > MAX_LIMIT) {
            throw new InvalidSearchRequestException(
                    "Search limit must be between 1 and " + MAX_LIMIT);
        }
        return rawLimit;
    }

    private <T> T execute(Supplier<T> query) {
        try {
            return query.get();
        } catch (RuntimeException ex) {
            if (isTimeout(ex)) {
                throw new SearchTimeoutException();
            }
            throw ex;
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof jakarta.persistence.QueryTimeoutException
                    || current instanceof org.springframework.dao.QueryTimeoutException
                    || current instanceof SQLTimeoutException
                    || current instanceof TransactionTimedOutException) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}
