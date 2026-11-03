package com.monitoring.healthcheck.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.monitoring.healthcheck.entity.SyntheticHttpMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One HTTP request in a multi-step API workflow.
 *
 * <p>Values from {@link #extractVariables} are available to later steps as
 * <code>{{variable}}</code> placeholders in the URL, header values and body. Configured
 * definitions are validated before they are stored; the executor validates every resolved URL
 * again right before it is called.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyntheticApiStep {

    private String name;

    private SyntheticHttpMethod method;

    private String url;

    /** Request headers; values may reference previously extracted variables. */
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<>();

    /** Raw request body, may reference previously extracted variables. */
    private String body;

    /**
     * Variable name to extraction expression, for example {@code token -> jsonpath:$.access_token}
     * or {@code trace -> regex:([a-f0-9]{16})}.
     */
    @Builder.Default
    private Map<String, String> extractVariables = new LinkedHashMap<>();

    /** Assertions evaluated in order; the step fails on the first failed assertion. */
    @Builder.Default
    private List<SyntheticAssertion> assertions = List.of();
}
