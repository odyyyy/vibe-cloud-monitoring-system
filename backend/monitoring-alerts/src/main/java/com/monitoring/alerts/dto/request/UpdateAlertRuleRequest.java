package com.monitoring.alerts.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.LogicOperator;
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
 * Partial update of an alert rule: only non-null fields are applied. Ranges are validated when
 * a field is present. When {@code conditions} is present the condition list is replaced entirely.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAlertRuleRequest {

    @Size(min = 1, max = 100, message = "name must be between 1 and 100 characters")
    private String name;

    private Long applicationId;

    private AlertRuleType type;

    @Size(min = 1, max = 255, message = "target must be between 1 and 255 characters")
    private String target;

    private AlertCondition condition;

    private Double threshold;

    @Valid
    @Size(max = 10, message = "at most 10 conditions are allowed")
    private List<AlertConditionRequest> conditions;

    private LogicOperator logicOperator;

    @Min(value = 1, message = "durationMinutes must be between 1 and 1440")
    @Max(value = 1440, message = "durationMinutes must be between 1 and 1440")
    private Integer durationMinutes;

    @JsonProperty("isActive")
    private Boolean active;
}
