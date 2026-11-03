package com.monitoring.healthcheck.dto;

/**
 * Outcome of one step inside a multi-step workflow.
 *
 * <p>{@link #SKIPPED} is recorded for steps that were not executed because an earlier step
 * failed or the workflow budget was exhausted. A skipped step is never reported as passed.
 */
public enum StepExecutionStatus {
    PASSED,
    FAILED,
    SKIPPED
}
