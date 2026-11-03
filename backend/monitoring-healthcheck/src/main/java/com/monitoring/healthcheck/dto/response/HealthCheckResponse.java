package com.monitoring.healthcheck.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.healthcheck.dto.SyntheticApiStep;
import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthCheckType;
import com.monitoring.healthcheck.service.SyntheticCheckCodec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Public representation of a health check configuration.
 *
 * <p>The JSON field is {@code isActive} (not the Jackson default {@code active}) to match the
 * documented frontend contract. {@code steps} is only populated for multi-step workflows;
 * {@code targetUrl} is {@code null} for them.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheckResponse {

    private Long id;
    private Long organizationId;
    private String name;
    private HealthCheckType type;
    private String targetUrl;
    private HealthCheckMethod method;
    private int expectedStatusCode;
    private int intervalSeconds;
    private List<SyntheticApiStep> steps;

    @JsonProperty("isActive")
    private boolean active;

    public static HealthCheckResponse fromEntity(HealthCheckConfig config, SyntheticCheckCodec codec) {
        List<SyntheticApiStep> steps = config.getType() == HealthCheckType.MULTI_STEP_API
                ? codec.readSteps(config.getStepsJson())
                : null;
        return HealthCheckResponse.builder()
                .id(config.getId())
                .organizationId(config.getOrganizationId())
                .name(config.getName())
                .type(config.getType())
                .targetUrl(config.getTargetUrl())
                .method(config.getMethod())
                .expectedStatusCode(config.getExpectedStatusCode())
                .intervalSeconds(config.getIntervalSeconds())
                .steps(steps)
                .active(config.isActive())
                .build();
    }
}
