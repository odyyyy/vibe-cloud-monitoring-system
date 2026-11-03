package com.monitoring.healthcheck.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One assertion evaluated against a workflow step response.
 *
 * <p>Supported types:
 * <ul>
 *     <li>{@code STATUS_CODE} — {@code operator} {@code ==}/{@code !=}, numeric {@code value};</li>
 *     <li>{@code BODY_CONTAINS} — {@code operator} {@code contains}/{@code not_contains},
 *         string {@code value};</li>
 *     <li>{@code JSON_PATH} — {@code path} plus {@code operator} {@code ==}/{@code !=}/
 *         {@code contains}/{@code not_contains} and a string {@code value}.</li>
 * </ul>
 *
 * <p>The {@code value} is untrusted input and is only ever compared as data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyntheticAssertion {

    /** {@code STATUS_CODE}, {@code BODY_CONTAINS} or {@code JSON_PATH}. */
    private String type;

    /** Comparison operator; the allowed set depends on {@link #type}. */
    private String operator;

    /** Expected value. Status codes are persisted as numbers, everything else as strings. */
    private Object value;

    /** Simple JSON path (for {@code JSON_PATH} assertions), for example {@code $.status}. */
    private String path;

    /** Human readable description used in failure messages; never contains the response body. */
    public String describe() {
        StringBuilder description = new StringBuilder();
        description.append(type == null ? "assertion" : type);
        if (path != null && !path.isBlank()) {
            description.append(' ').append(path);
        }
        description.append(' ').append(operator == null ? "?" : operator);
        if (value != null) {
            description.append(' ').append(value);
        }
        return description.toString();
    }
}
