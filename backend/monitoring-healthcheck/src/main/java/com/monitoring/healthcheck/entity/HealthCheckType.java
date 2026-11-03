package com.monitoring.healthcheck.entity;

/**
 * Kind of synthetic check.
 *
 * <p>{@link #SINGLE_HTTP} calls one endpoint with a configurable expected status code.
 * {@link #MULTI_STEP_API} runs a bounded sequence of HTTP requests, passing extracted variables
 * between steps and evaluating assertions after each response.
 */
public enum HealthCheckType {
    SINGLE_HTTP,
    MULTI_STEP_API
}
