package com.monitoring.collector.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parameters for listing the distinct metric names known for an application.
 * The owning application is resolved from the authenticated user, never trusted
 * from the request on its own.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricNamesRequest {

    @NotNull(message = "applicationId is required")
    @Positive(message = "applicationId must be positive")
    private Long applicationId;
}
