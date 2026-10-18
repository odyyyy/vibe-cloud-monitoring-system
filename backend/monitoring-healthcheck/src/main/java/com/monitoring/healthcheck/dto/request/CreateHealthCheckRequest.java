package com.monitoring.healthcheck.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
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

/**
 * Request to create a health check. SSRF validation of {@code targetUrl} is enforced in the
 * service layer (bean validation only covers shape and ranges).
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

    @NotBlank(message = "targetUrl is required")
    @Size(max = 2048, message = "targetUrl must be at most 2048 characters")
    private String targetUrl;

    @NotNull(message = "method is required")
    private HealthCheckMethod method;

    @NotNull(message = "expectedStatusCode is required")
    @Min(value = 100, message = "expectedStatusCode must be between 100 and 599")
    @Max(value = 599, message = "expectedStatusCode must be between 100 and 599")
    private Integer expectedStatusCode;

    @NotNull(message = "intervalSeconds is required")
    @Min(value = 10, message = "intervalSeconds must be at least 10")
    @Max(value = 86400, message = "intervalSeconds must be at most 86400")
    private Integer intervalSeconds;

    @JsonProperty("isActive")
    private Boolean active;
}
