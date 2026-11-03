package com.monitoring.healthcheck.dto.response;

import com.monitoring.healthcheck.dto.StepExecutionResult;
import com.monitoring.healthcheck.entity.HealthCheckResult;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.service.SyntheticCheckCodec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

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
    private List<StepExecutionResult> stepResults;

    public static HealthCheckResultResponse fromEntity(HealthCheckResult result, SyntheticCheckCodec codec) {
        List<StepExecutionResult> stepResults = result.getStepResults() == null
                ? null
                : codec.readStepResults(result.getStepResults());
        return HealthCheckResultResponse.builder()
                .id(result.getId())
                .configId(result.getConfig().getId())
                .status(result.getStatus())
                .responseTimeMs(result.getResponseTimeMs())
                .errorMessage(result.getErrorMessage())
                .checkedAt(result.getCheckedAt())
                .stepResults(stepResults)
                .build();
    }
}
