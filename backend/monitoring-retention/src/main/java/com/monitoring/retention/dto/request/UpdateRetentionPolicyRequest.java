package com.monitoring.retention.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for updating an organization's retention policy.
 *
 * <p>Only a lower bound is expressed here; the per-type upper bound depends on runtime
 * configuration and is enforced by the service so the API does not hard-code limits.
 */
public record UpdateRetentionPolicyRequest(
        @NotNull @Min(1) Integer logsRetentionDays,
        @NotNull @Min(1) Integer metricsRetentionDays,
        @NotNull @Min(1) Integer tracesRetentionDays
) {
}
