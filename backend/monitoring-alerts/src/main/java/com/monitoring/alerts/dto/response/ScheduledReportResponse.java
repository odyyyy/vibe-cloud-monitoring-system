package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.entity.ScheduledReportChannelType;
import com.monitoring.alerts.entity.ScheduledReportFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Public representation of a scheduled report, including the next computed run time. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledReportResponse {

    private Long id;
    private Long organizationId;
    private String name;
    private ScheduledReportFrequency frequency;
    private Integer dayOfWeek;
    private LocalTime timeOfDay;
    private String timeZone;
    private ScheduledReportChannelType channelType;
    private Long channelId;
    private List<String> recipients;
    private boolean includeMetrics;
    private boolean includeTopErrors;
    private boolean includeUptime;
    private Instant lastRunAt;
    private Instant nextRunAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static ScheduledReportResponse from(ScheduledReport report, Instant nextRunAt, String timeZone) {
        return ScheduledReportResponse.builder()
                .id(report.getId())
                .organizationId(report.getOrganizationId())
                .name(report.getName())
                .frequency(report.getFrequency())
                .dayOfWeek(report.getDayOfWeek())
                .timeOfDay(report.getTimeOfDay())
                .timeZone(timeZone)
                .channelType(report.getChannelType())
                .channelId(report.getChannelId())
                .recipients(List.copyOf(report.getRecipients()))
                .includeMetrics(report.isIncludeMetrics())
                .includeTopErrors(report.isIncludeTopErrors())
                .includeUptime(report.isIncludeUptime())
                .lastRunAt(report.getLastRunAt())
                .nextRunAt(nextRunAt)
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
