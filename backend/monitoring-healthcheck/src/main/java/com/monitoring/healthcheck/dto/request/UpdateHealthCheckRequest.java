package com.monitoring.healthcheck.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.healthcheck.dto.SyntheticApiStep;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthCheckType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Partial update of a health check: only non-null fields are applied. Ranges are validated
 * when a field is present. Switching {@code type} requires the fields the new type needs
 * (a target URL for single HTTP checks, steps for multi-step workflows).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateHealthCheckRequest {

    @Size(min = 1, max = 100, message = "name must be between 1 and 100 characters")
    private String name;

    private HealthCheckType type;

    @Size(max = 2048, message = "targetUrl must be between 1 and 2048 characters")
    private String targetUrl;

    private HealthCheckMethod method;

    @Min(value = 100, message = "expectedStatusCode must be between 100 and 599")
    @Max(value = 599, message = "expectedStatusCode must be between 100 and 599")
    private Integer expectedStatusCode;

    @Min(value = 10, message = "intervalSeconds must be at least 10")
    @Max(value = 86400, message = "intervalSeconds must be at most 86400")
    private Integer intervalSeconds;

    @Valid
    @Size(max = 5, message = "steps must contain at most 5 items")
    private List<SyntheticApiStep> steps;

    @JsonProperty("isActive")
    private Boolean active;
}
