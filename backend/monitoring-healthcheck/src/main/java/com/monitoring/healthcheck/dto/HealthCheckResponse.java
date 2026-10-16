package com.monitoring.healthcheck.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Public representation of a health check configuration.
 *
 * <p>The JSON field is {@code isActive} (not the Jackson default {@code active}) to match the
 * documented frontend contract.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheckResponse {

    private Long id;
    private Long userId;
    private String name;
    private String targetUrl;
    private HealthCheckMethod method;
    private int expectedStatusCode;
    private int intervalSeconds;

    @JsonProperty("isActive")
    private boolean active;

    public static HealthCheckResponse fromEntity(HealthCheckConfig config) {
        return HealthCheckResponse.builder()
                .id(config.getId())
                .userId(config.getOwnerId())
                .name(config.getName())
                .targetUrl(config.getTargetUrl())
                .method(config.getMethod())
                .expectedStatusCode(config.getExpectedStatusCode())
                .intervalSeconds(config.getIntervalSeconds())
                .active(config.isActive())
                .build();
    }
}
