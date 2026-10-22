package com.monitoring.alerts.dto.request;

import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.collector.entity.LogLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Read-only calibration request for a log alert: counts matching events in the last
 * {@link #windowMinutes} minutes. The organization is always resolved from the authenticated
 * principal, never from the body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewLogCountRequest {

    @NotNull(message = "applicationId is required")
    @Positive(message = "applicationId must be positive")
    private Long applicationId;

    private LogLevel logLevel;

    @Size(max = AlertRule.MAX_LOG_QUERY_LENGTH,
            message = "logQuery must be at most " + AlertRule.MAX_LOG_QUERY_LENGTH + " characters")
    private String logQuery;

    @NotNull(message = "windowMinutes is required")
    @Min(value = AlertRule.MIN_WINDOW_MINUTES,
            message = "windowMinutes must be between 1 and 60")
    @Max(value = AlertRule.MAX_WINDOW_MINUTES,
            message = "windowMinutes must be between 1 and 60")
    private Integer windowMinutes;
}
