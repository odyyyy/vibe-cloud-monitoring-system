package com.monitoring.healthcheck.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-step diagnostic recorded with a workflow result.
 *
 * <p>Only metadata is stored: extracted variable values and response bodies are deliberately
 * omitted because they can contain credentials or customer data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StepExecutionResult {

    private String name;

    private StepExecutionStatus status;

    private Integer statusCode;

    private int responseTimeMs;

    /** Untrusted, length-bounded diagnostic text. Never contains a response body. */
    private String errorMessage;
}
