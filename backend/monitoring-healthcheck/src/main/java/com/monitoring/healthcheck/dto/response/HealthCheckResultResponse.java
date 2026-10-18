package com.monitoring.healthcheck.dto.response;

import com.monitoring.healthcheck.entity.HealthCheckResult;
import com.monitoring.healthcheck.entity.HealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheckResultResponse {

    private Long id;
    private Long configId;
    private HealthStatus status;
    private int responseTimeMs;
    private String errorMessage;
    private Instant checkedAt;

    public static HealthCheckResultResponse fromEntity(HealthCheckResult result) {
        return HealthCheckResultResponse.builder()
                .id(result.getId())
                .configId(result.getConfig().getId())
                .status(result.getStatus())
                .responseTimeMs(result.getResponseTimeMs())
                .errorMessage(result.getErrorMessage())
                .checkedAt(result.getCheckedAt())
                .build();
    }
}
