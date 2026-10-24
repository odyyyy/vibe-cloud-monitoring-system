package com.monitoring.auth.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatScopePolicyTest {

    @Test
    void metricsReadRequiresReadMetrics() {
        assertThat(PatScopePolicy.requiredScope("GET", "/api/v1/metrics/names"))
                .contains(PatScopes.READ_METRICS);
        assertThat(PatScopePolicy.requiredScope("GET", "/api/v1/metrics/query"))
                .contains(PatScopes.READ_METRICS);
    }

    @Test
    void dashboardMutationsRequireWriteDashboards() {
        assertThat(PatScopePolicy.requiredScope("POST", "/api/v1/dashboards"))
                .contains(PatScopes.WRITE_DASHBOARDS);
        assertThat(PatScopePolicy.requiredScope("PUT", "/api/v1/dashboards/7"))
                .contains(PatScopes.WRITE_DASHBOARDS);
        assertThat(PatScopePolicy.requiredScope("DELETE", "/api/v1/dashboards/7/widgets/3"))
                .contains(PatScopes.WRITE_DASHBOARDS);
    }

    @Test
    void dashboardReadRequiresReadDashboards() {
        assertThat(PatScopePolicy.requiredScope("GET", "/api/v1/dashboards"))
                .contains(PatScopes.READ_DASHBOARDS);
        assertThat(PatScopePolicy.requiredScope("GET", "/api/v1/dashboards/7"))
                .contains(PatScopes.READ_DASHBOARDS);
    }

    @Test
    void unknownRouteHasNoScopeAndIsNotPublic() {
        assertThat(PatScopePolicy.requiredScope("GET", "/api/v1/organizations")).isEmpty();
        assertThat(PatScopePolicy.isPublic("/api/v1/organizations")).isFalse();
        assertThat(PatScopePolicy.requiredScope("GET", "/api/v1/settings/pats")).isEmpty();
        assertThat(PatScopePolicy.isPublic("/api/v1/settings/pats")).isFalse();
    }

    @Test
    void publicRoutesAreSkipped() {
        assertThat(PatScopePolicy.isPublic("/api/v1/dashboards/share/abc")).isTrue();
        assertThat(PatScopePolicy.isPublic("/api/auth/login")).isTrue();
        assertThat(PatScopePolicy.isPublic("/api/v1/ingest/metrics")).isTrue();
        assertThat(PatScopePolicy.isPublic("/actuator/health")).isTrue();
        assertThat(PatScopePolicy.isPublic("/error")).isTrue();
    }

    @Test
    void prefixMatchingRespectsSegmentBoundaries() {
        assertThat(PatScopePolicy.requiredScope("GET", "/api/v1/dashboards-something")).isEmpty();
        assertThat(PatScopePolicy.requiredScope("GET", "/api/v1/metricsx")).isEmpty();
    }

    @Test
    void applicationsAndAlertsAreMapped() {
        assertThat(PatScopePolicy.requiredScope("GET", "/api/applications/5/agents"))
                .contains(PatScopes.READ_APPLICATIONS);
        assertThat(PatScopePolicy.requiredScope("DELETE", "/api/applications/5"))
                .contains(PatScopes.WRITE_APPLICATIONS);
        assertThat(PatScopePolicy.requiredScope("GET", "/api/v1/alert-incidents"))
                .contains(PatScopes.READ_ALERTS);
        assertThat(PatScopePolicy.requiredScope("GET", "/api/v1/notification-deliveries"))
                .contains(PatScopes.READ_ALERTS);
        assertThat(PatScopePolicy.requiredScope("POST", "/api/v1/alert-rules"))
                .contains(PatScopes.WRITE_ALERTS);
    }
}
