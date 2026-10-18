package com.monitoring.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Standard error body returned by the API. {@code fieldErrors} is only present for validation
 * failures, so a plain message error omits it from the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, String message, Map<String, String> fieldErrors) {

    public ApiError(int status, String message) {
        this(status, message, null);
    }
}
