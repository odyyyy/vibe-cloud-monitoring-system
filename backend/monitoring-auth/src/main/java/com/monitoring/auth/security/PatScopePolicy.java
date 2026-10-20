package com.monitoring.auth.security;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps an authenticated Personal Access Token request to the scope it requires.
 *
 * <p>The policy is deliberately deny-by-default: only the routes listed in {@link #RULES}
 * are reachable with a token, and any other route requires a scope that no token holds in
 * practice, so it is rejected with {@code 403}. Public routes (login, ingestion, public
 * dashboard shares, actuator) are skipped entirely because they do not depend on a user
 * identity.
 *
 * <p>Rules are matched by exact path or path prefix on a segment boundary, so
 * {@code /api/v1/dashboards} does not accidentally match
 * {@code /api/v1/dashboards-something}.
 */
public final class PatScopePolicy {

    /** Routes that never require a token scope (authentication is optional or absent). */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/",
            "/api/hello",
            "/actuator/",
            "/v3/api-docs",
            "/swagger-ui",
            "/error",
            "/api/v1/ingest/",
            "/api/ingest/",
            "/h2-console/",
            "/api/v1/dashboards/share");

    private static final List<Rule> RULES = List.of(
            new Rule("GET", "/api/v1/metrics", PatScopes.READ_METRICS),
            new Rule("GET", "/api/v1/logs", PatScopes.READ_LOGS),
            new Rule("GET", "/api/v1/saved-log-views", PatScopes.READ_LOGS),
            new Rule("GET", "/api/v1/traces", PatScopes.READ_TRACES),
            new Rule("GET", "/api/v1/dashboards", PatScopes.READ_DASHBOARDS),
            new Rule("POST", "/api/v1/dashboards", PatScopes.WRITE_DASHBOARDS),
            new Rule("PUT", "/api/v1/dashboards", PatScopes.WRITE_DASHBOARDS),
            new Rule("PATCH", "/api/v1/dashboards", PatScopes.WRITE_DASHBOARDS),
            new Rule("DELETE", "/api/v1/dashboards", PatScopes.WRITE_DASHBOARDS),
            new Rule("GET", "/api/v1/health-checks", PatScopes.READ_HEALTH),
            new Rule("POST", "/api/v1/health-checks", PatScopes.WRITE_HEALTH),
            new Rule("PUT", "/api/v1/health-checks", PatScopes.WRITE_HEALTH),
            new Rule("DELETE", "/api/v1/health-checks", PatScopes.WRITE_HEALTH),
            new Rule("GET", "/api/v1/alert-rules", PatScopes.READ_ALERTS),
            new Rule("GET", "/api/v1/alert-incidents", PatScopes.READ_ALERTS),
            new Rule("GET", "/api/v1/notification-channels", PatScopes.READ_ALERTS),
            new Rule("POST", "/api/v1/alert-rules", PatScopes.WRITE_ALERTS),
            new Rule("PUT", "/api/v1/alert-rules", PatScopes.WRITE_ALERTS),
            new Rule("DELETE", "/api/v1/alert-rules", PatScopes.WRITE_ALERTS),
            new Rule("POST", "/api/v1/alert-incidents", PatScopes.WRITE_ALERTS),
            new Rule("PUT", "/api/v1/alert-incidents", PatScopes.WRITE_ALERTS),
            new Rule("POST", "/api/v1/notification-channels", PatScopes.WRITE_ALERTS),
            new Rule("PUT", "/api/v1/notification-channels", PatScopes.WRITE_ALERTS),
            new Rule("DELETE", "/api/v1/notification-channels", PatScopes.WRITE_ALERTS),
            new Rule("GET", "/api/applications", PatScopes.READ_APPLICATIONS),
            new Rule("POST", "/api/applications", PatScopes.WRITE_APPLICATIONS),
            new Rule("PUT", "/api/applications", PatScopes.WRITE_APPLICATIONS),
            new Rule("DELETE", "/api/applications", PatScopes.WRITE_APPLICATIONS),
            new Rule("GET", "/api/v1/overview", PatScopes.READ_OVERVIEW),
            new Rule("GET", "/api/v1/usage", PatScopes.READ_USAGE),
            new Rule("GET", "/api/v1/audit-events", PatScopes.READ_AUDIT));

    private PatScopePolicy() {
    }

    /** True when the request target does not depend on a user identity at all. */
    public static boolean isPublic(String uri) {
        if (uri == null) {
            return false;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    /**
     * The scope required by the request, or {@link Optional#empty()} when the route is not
     * subject to scope enforcement (callers treat empty as "deny" unless {@link #isPublic}).
     */
    public static Optional<String> requiredScope(String method, String uri) {
        if (method == null || uri == null) {
            return Optional.empty();
        }
        String normalizedMethod = method.toUpperCase(Locale.ROOT);
        return RULES.stream()
                .filter(rule -> rule.method().equals(normalizedMethod) && matchesPath(uri, rule.prefix()))
                .map(Rule::scope)
                .findFirst();
    }

    static boolean matchesPath(String uri, String prefix) {
        if (uri.equals(prefix)) {
            return true;
        }
        return uri.startsWith(prefix + "/");
    }

    private record Rule(String method, String prefix, String scope) {
    }
}
