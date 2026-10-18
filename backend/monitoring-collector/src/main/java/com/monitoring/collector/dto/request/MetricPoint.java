package com.monitoring.collector.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

/**
 * A single metric point inside an ingestion batch.
 * {@code timestamp} is the measurement instant in UTC.
 * {@code tags} are optional labels, for example {"host": "server-1", "env": "prod"};
 * tag names and values are bounded to the widths of their storage columns.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricPoint {

    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_TAGS = 50;
    public static final int MAX_TAG_KEY_LENGTH = 100;
    public static final int MAX_TAG_VALUE_LENGTH = 500;

    @NotBlank(message = "metric name is required")
    @Size(max = MAX_NAME_LENGTH, message = "metric name must be at most " + MAX_NAME_LENGTH + " characters")
    private String name;

    @NotNull(message = "metric value is required")
    private Double value;

    @NotNull(message = "metric timestamp is required")
    private Instant timestamp;

    @Size(max = MAX_TAGS, message = "at most " + MAX_TAGS + " tags are allowed per metric")
    private Map<
            @NotBlank(message = "tag keys must not be blank")
            @Size(max = MAX_TAG_KEY_LENGTH, message = "tag keys must be at most " + MAX_TAG_KEY_LENGTH + " characters") String,
            @NotNull(message = "tag values must not be null")
            @Size(max = MAX_TAG_VALUE_LENGTH, message = "tag values must be at most " + MAX_TAG_VALUE_LENGTH + " characters") String> tags;

    /** Rejects NaN and infinities, which JSON parsers accept but databases cannot store. */
    @JsonIgnore
    @AssertTrue(message = "value must be a finite number")
    public boolean isValueFinite() {
        return value == null || Double.isFinite(value);
    }
}
