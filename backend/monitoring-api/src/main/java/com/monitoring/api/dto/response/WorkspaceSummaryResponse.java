package com.monitoring.api.dto.response;

/**
 * Aggregated counters for the Overview page of the authenticated user's workspace.
 *
 * <p>All counters are derived from data owned by the caller only: the service resolves the
 * user's application ids and never accepts a tenant identifier from the request.
 *
 * @param applicationCount  applications (agents) owned by the user, all-time
 * @param metricNameCount24h distinct metric names received in the last 24 hours
 * @param logEventCount24h   log events received in the last 24 hours
 */
public record WorkspaceSummaryResponse(
        long applicationCount,
        long metricNameCount24h,
        long logEventCount24h
) {
}
