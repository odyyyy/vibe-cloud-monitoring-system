package com.monitoring.healthcheck.entity;

/**
 * HTTP method allowed inside a multi-step API workflow.
 *
 * <p>Kept separate from {@link HealthCheckMethod} so single-step checks keep their small,
 * stable contract while workflows can express the verbs an API chain usually needs.
 */
public enum SyntheticHttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD;

    /** Methods that must not carry a request body. */
    public boolean forbidsBody() {
        return this == GET || this == HEAD;
    }
}
