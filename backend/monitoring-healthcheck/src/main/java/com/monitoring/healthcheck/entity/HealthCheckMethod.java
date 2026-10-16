package com.monitoring.healthcheck.entity;

/** HTTP method used by a synthetic check. Only safe, body-less methods are allowed. */
public enum HealthCheckMethod {
    GET,
    POST
}
