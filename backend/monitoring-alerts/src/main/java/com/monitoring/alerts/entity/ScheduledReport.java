package com.monitoring.alerts.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A recurring digest of platform telemetry, delivered by email or Slack.
 *
 * <p>Reports are strictly tenant-owned through {@code organizationId}. Email reports carry an
 * explicit, validated recipient list; Slack reports reference one {@link NotificationChannel} of
 * type {@code SLACK}. {@code lastRunAt} is internal scheduler state that prevents the same
 * occurrence from being delivered twice when the application restarts within the scheduled hour.
 */
@Entity
@Table(
        name = "scheduled_reports",
        indexes = {
                @Index(name = "idx_scheduled_reports_organization_id", columnList = "organization_id"),
                @Index(name = "idx_scheduled_reports_time_of_day", columnList = "time_of_day")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledReport {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_RECIPIENTS = 10;
    public static final int MAX_RECIPIENT_LENGTH = 320;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private ScheduledReportFrequency frequency;

    /** ISO day-of-week (1 = Monday .. 7 = Sunday); only used by WEEKLY reports. */
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    /** Scheduled time in the report time zone; MONTHLY reports run on the first day of the month. */
    @Column(name = "time_of_day", nullable = false)
    private LocalTime timeOfDay;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "channel_type", nullable = false, length = 16)
    private ScheduledReportChannelType channelType;

    /** Slack notification channel; null for EMAIL reports. */
    @Column(name = "channel_id")
    private Long channelId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "scheduled_report_recipients",
            joinColumns = @JoinColumn(name = "report_id"))
    @OrderColumn(name = "recipient_order")
    @Column(name = "recipient", nullable = false, length = MAX_RECIPIENT_LENGTH)
    @Builder.Default
    private List<String> recipients = new ArrayList<>();

    @Column(name = "include_metrics", nullable = false)
    private boolean includeMetrics;

    @Column(name = "include_top_errors", nullable = false)
    private boolean includeTopErrors;

    @Column(name = "include_uptime", nullable = false)
    private boolean includeUptime;

    /** Internal: instant of the last successful scheduler run. */
    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
