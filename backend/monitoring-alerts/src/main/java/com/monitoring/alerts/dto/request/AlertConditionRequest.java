package com.monitoring.alerts.dto.request;

import com.monitoring.alerts.entity.AlertCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single condition inside a compound alert rule. For metric rules {@code target} is a metric
 * name; for health-check rules it is a health-check id. Target ownership is verified in the
 * service layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertConditionRequest {

    private static final int TARGET_MAX_LENGTH = 255;

    @NotBlank(message = "condition target is required")
    @Size(max = TARGET_MAX_LENGTH, message = "condition target must be at most 255 characters")
    private String target;

    @NotNull(message = "condition operator is required")
    private AlertCondition operator;

    @NotNull(message = "condition threshold is required")
    private Double threshold;
}
