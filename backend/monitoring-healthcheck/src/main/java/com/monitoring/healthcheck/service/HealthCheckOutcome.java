package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.dto.StepExecutionResult;
import com.monitoring.healthcheck.entity.HealthStatus;

import java.util.List;

/**
 * Result of executing a single check. {@code errorMessage} is untrusted text and is length
 * bounded before it is persisted. {@code stepResults} is only populated for multi-step workflows.
 */
public record HealthCheckOutcome(
        HealthStatus status,
        int responseTimeMs,
        String errorMessage,
        List<StepExecutionResult> stepResults) {

    public HealthCheckOutcome {
        stepResults = stepResults == null ? List.of() : List.copyOf(stepResults);
    }

    /** Convenience for single-step checks and existing callers. */
    public HealthCheckOutcome(HealthStatus status, int responseTimeMs, String errorMessage) {
        this(status, responseTimeMs, errorMessage, List.of());
    }
}
