package com.monitoring.alerts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
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
 * Request to create an alert rule. Type-dependent consistency (application present for metric
 * rules, target referencing an owned health check) is enforced in the service layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAlertRuleRequest {

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    /** Required for {@code METRIC_THRESHOLD}; ignored (may be null) for {@code HEALTH_CHECK}. */
    private Long applicationId;

    @NotNull(message = "type is required")
    private AlertRuleType type;

    @NotBlank(message = "target is required")
    @Size(max = 255, message = "target must be at most 255 characters")
    private String target;

    @NotNull(message = "condition is required")
    private AlertCondition condition;

    @NotNull(message = "threshold is required")
    private Double threshold;

    @NotNull(message = "durationMinutes is required")
    @Min(value = 1, message = "durationMinutes must be between 1 and 1440")
    @Max(value = 1440, message = "durationMinutes must be between 1 and 1440")
    private Integer durationMinutes;

    @JsonProperty("isActive")
    private Boolean active;
}
