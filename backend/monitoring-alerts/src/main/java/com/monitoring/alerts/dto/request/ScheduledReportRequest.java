package com.monitoring.alerts.dto.request;

import com.monitoring.alerts.entity.ScheduledReportChannelType;
import com.monitoring.alerts.entity.ScheduledReportFrequency;
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

import java.time.LocalTime;
import java.util.List;

/** Create/update payload for a scheduled report. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledReportRequest {

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @NotNull(message = "frequency is required")
    private ScheduledReportFrequency frequency;

    @Min(value = 1, message = "dayOfWeek must be between 1 and 7")
    @Max(value = 7, message = "dayOfWeek must be between 1 and 7")
    private Integer dayOfWeek;

    @NotNull(message = "timeOfDay is required")
    private LocalTime timeOfDay;

    @NotNull(message = "channelType is required")
    private ScheduledReportChannelType channelType;

    private Long channelId;

    @Size(max = 10, message = "At most 10 recipients are allowed")
    private List<@NotBlank(message = "recipient must not be blank")
            @Size(max = 320, message = "recipient must be at most 320 characters") String> recipients;

    @NotNull(message = "includeMetrics is required")
    private Boolean includeMetrics;

    @NotNull(message = "includeTopErrors is required")
    private Boolean includeTopErrors;

    @NotNull(message = "includeUptime is required")
    private Boolean includeUptime;
}
