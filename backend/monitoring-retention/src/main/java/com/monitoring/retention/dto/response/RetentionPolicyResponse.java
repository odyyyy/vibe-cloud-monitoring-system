package com.monitoring.retention.dto.response;

import java.time.Instant;

/**
 * Effective retention policy for the active organization.
 *
 * <p>{@code updatedAt} and {@code updatedBy} are {@code null} while the organization still
 * uses the platform defaults. The {@code max*RetentionDays} fields expose the configured hard
 * limits so the frontend can render accurate min/max hints without duplicating configuration.
 */
public record RetentionPolicyResponse(
        Long organizationId,
        int logsRetentionDays,
        int metricsRetentionDays,
        int tracesRetentionDays,
        Instant updatedAt,
        Long updatedBy,
        int maxLogsRetentionDays,
        int maxMetricsRetentionDays,
        int maxTracesRetentionDays
) {
}
