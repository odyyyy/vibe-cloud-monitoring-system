package com.monitoring.collector.security;

import com.monitoring.collector.config.IngestionQuotaProperties;
import com.monitoring.apps.entity.Application;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Rate-limits authenticated ingestion requests per application and endpoint category.
 *
 * <p>It runs after {@link ApiKeyAuthFilter} on the ingestion security chain, so the caller has
 * already been authenticated and the owning {@link Application} is available as the
 * authentication principal. The bucket key is the application id plus the endpoint category, so
 * one application cannot exhaust another application's quota and the metrics and logs paths have
 * independent limits.
 *
 * <p>When a request exceeds its quota the response is HTTP 429 with a {@code Retry-After} header
 * and a small JSON body; a WARN is logged with a masked API key, and the rejection is counted in
 * the rate-limited metric. Unknown ingestion paths are not limited (fail open) to avoid breaking
 * future endpoints that have no configured quota.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = ApiKeyAuthFilter.API_KEY_HEADER;

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final IngestionRateLimiter rateLimiter;
    private final IngestionQuotaProperties quotaProperties;

    public RateLimitFilter(IngestionRateLimiter rateLimiter, IngestionQuotaProperties quotaProperties) {
        this.rateLimiter = rateLimiter;
        this.quotaProperties = quotaProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!quotaProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = bucketFor(request.getRequestURI());
        Application application = currentApplication();
        if (bucket == null || application == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = application.getId() + ":" + bucket.category();
        RateLimitDecision decision = rateLimiter.tryAcquire(key, bucket.limit(), now());
        if (!decision.allowed()) {
            reject(request, response, application, decision);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Bucket bucketFor(String uri) {
        if (uri.endsWith("/metrics")) {
            return new Bucket("metrics", quotaProperties.getMetricsPerMinute());
        }
        if (uri.endsWith("/logs")) {
            return new Bucket("logs", quotaProperties.getLogsPerMinute());
        }
        return null;
    }

    private void reject(HttpServletRequest request,
                        HttpServletResponse response,
                        Application application,
                        RateLimitDecision decision) throws IOException {
        rateLimiter.recordRejection(application.getId());
        log.warn("Rate limit exceeded for applicationId={} key={} path={} (limit={}/min); retry after {}s",
                application.getId(),
                mask(request.getHeader(API_KEY_HEADER)),
                request.getRequestURI(),
                decision.limit(),
                decision.retryAfterSeconds());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retry_after_seconds\":"
                + decision.retryAfterSeconds() + "}");
    }

    /** Current time; overridable in tests to make window boundaries deterministic. */
    Instant now() {
        return Instant.now();
    }

    private Application currentApplication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Application application) {
            return application;
        }
        return null;
    }

    private static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /** Endpoint category and its configured per-minute limit. */
    private record Bucket(String category, int limit) {
    }
}
