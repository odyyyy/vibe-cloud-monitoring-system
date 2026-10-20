package com.monitoring.auth.security;

import java.util.List;
import java.util.Set;

/**
 * The allowlist of scopes a Personal Access Token may request.
 *
 * <p>Scopes are coarse, resource-oriented permissions. A token only ever narrows the
 * permissions of the user that created it: the organization role ({@code @RequireRole})
 * is still enforced on top of the scope check.
 */
public final class PatScopes {

    public static final String READ_METRICS = "read:metrics";
    public static final String READ_LOGS = "read:logs";
    public static final String READ_TRACES = "read:traces";
    public static final String READ_DASHBOARDS = "read:dashboards";
    public static final String WRITE_DASHBOARDS = "write:dashboards";
    public static final String READ_HEALTH = "read:health";
    public static final String WRITE_HEALTH = "write:health";
    public static final String READ_ALERTS = "read:alerts";
    public static final String WRITE_ALERTS = "write:alerts";
    public static final String READ_APPLICATIONS = "read:applications";
    public static final String WRITE_APPLICATIONS = "write:applications";
    public static final String READ_OVERVIEW = "read:overview";
    public static final String READ_USAGE = "read:usage";
    public static final String READ_AUDIT = "read:audit";

    /** All scopes accepted by the API, in the order shown to users. */
    public static final List<String> ALL = List.of(
            READ_METRICS,
            READ_LOGS,
            READ_TRACES,
            READ_DASHBOARDS,
            WRITE_DASHBOARDS,
            READ_HEALTH,
            WRITE_HEALTH,
            READ_ALERTS,
            WRITE_ALERTS,
            READ_APPLICATIONS,
            WRITE_APPLICATIONS,
            READ_OVERVIEW,
            READ_USAGE,
            READ_AUDIT);

    private static final Set<String> ALL_SET = Set.copyOf(ALL);

    private PatScopes() {
    }

    public static boolean isValid(String scope) {
        return scope != null && ALL_SET.contains(scope);
    }
}
