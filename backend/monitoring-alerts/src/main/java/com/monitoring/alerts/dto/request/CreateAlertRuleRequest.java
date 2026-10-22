package com.monitoring.alerts.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.collector.entity.LogLevel;
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
 * Request to create an alert rule.
 *
 * <p>A rule may be defined either as a compound list of {@link #conditions} combined by
 * {@link #logicOperator}, or (legacy/backward-compatible form) as a single
 * {@code condition}/{@code threshold} on {@code target}. Type-dependent consistency (application
 * present for metric rules, targets referencing owned resources) is enforced in the service layer.
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

    /** Legacy single-condition target. Optional when {@link #conditions} is provided. */
    @Size(max = 255, message = "target must be at most 255 characters")
    private String target;

    /** Legacy single-condition comparison. Optional when {@link #conditions} is provided. */
    private AlertCondition condition;

    /** Legacy single-condition threshold. Optional when {@link #conditions} is provided. */
    private Double threshold;

    /**
     * Compound conditions. When non-empty the rule is evaluated as a compound expression and the
     * legacy {@code target}/{@code condition}/{@code threshold} fields are ignored (but mirrored
     * from the first condition for backward compatibility).
     */
    @Valid
    @Size(max = 10, message = "at most 10 conditions are allowed")
    private List<AlertConditionRequest> conditions;

    /** How {@link #conditions} are combined; defaults to {@code AND}. */
    private LogicOperator logicOperator;

    /** Optional severity filter; only valid for {@code LOG} rules. */
    private LogLevel logLevel;

    /**
     * Optional literal substring filter (max 200 characters); only valid for {@code LOG} rules.
     * At least one of {@link #logLevel} and {@link #logQuery} is required for a log rule.
     */
    @Size(max = AlertRule.MAX_LOG_QUERY_LENGTH,
            message = "logQuery must be at most " + AlertRule.MAX_LOG_QUERY_LENGTH + " characters")
    private String logQuery;

    /** Count window in minutes; required for {@code LOG} rules and forbidden otherwise. */
    @Min(value = AlertRule.MIN_WINDOW_MINUTES,
            message = "windowMinutes must be between 1 and 60")
    @Max(value = AlertRule.MAX_WINDOW_MINUTES,
            message = "windowMinutes must be between 1 and 60")
    private Integer windowMinutes;

    /** Count comparison; required for {@code LOG} rules and forbidden otherwise. */
    private AlertCondition comparison;

    /** Count threshold; required for {@code LOG} rules and forbidden otherwise. */
    @Min(value = 0, message = "countThreshold must be greater than or equal to 0")
    private Long countThreshold;

    @NotNull(message = "durationMinutes is required")
    @Min(value = 1, message = "durationMinutes must be between 1 and 1440")
    @Max(value = 1440, message = "durationMinutes must be between 1 and 1440")
    private Integer durationMinutes;

    @JsonProperty("isActive")
    private Boolean active;
}
