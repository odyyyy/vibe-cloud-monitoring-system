package com.monitoring.healthcheck.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.healthcheck.dto.SyntheticApiStep;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthCheckType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request to create a health check. SSRF validation of {@code targetUrl} and every workflow step
 * is enforced in the service layer (bean validation only covers shape and ranges).
 *
 * <p>{@code targetUrl} is required for {@link HealthCheckType#SINGLE_HTTP} and ignored for
 * {@link HealthCheckType#MULTI_STEP_API}, which requires {@code steps} instead. The type defaults
 * to {@code SINGLE_HTTP} when omitted so existing clients keep working.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateHealthCheckRequest {

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    private HealthCheckType type;

    @Size(max = 2048, message = "targetUrl must be at most 2048 characters")
    private String targetUrl;

    private HealthCheckMethod method;

    @Min(value = 100, message = "expectedStatusCode must be between 100 and 599")
    @Max(value = 599, message = "expectedStatusCode must be between 100 and 599")
    private Integer expectedStatusCode;

    @NotNull(message = "intervalSeconds is required")
    @Min(value = 10, message = "intervalSeconds must be at least 10")
    @Max(value = 86400, message = "intervalSeconds must be at most 86400")
    private Integer intervalSeconds;

    @Valid
    @Size(max = 5, message = "steps must contain at most 5 items")
    private List<SyntheticApiStep> steps;

    @JsonProperty("isActive")
    private Boolean active;
}
