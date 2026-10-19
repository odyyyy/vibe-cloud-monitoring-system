package com.monitoring.collector.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Aggregated telemetry usage for one organization on one UTC calendar day.
 *
 * <p>The pair (organization, day) is unique, so the daily rollup can upsert without ever
 * producing duplicate rows. Counts are totals for the day (not deltas): the rollup recomputes
 * them from the stored telemetry, which makes a repeated run idempotent.
 */
@Entity
@Table(
        name = "daily_usage",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_daily_usage_organization_date",
                columnNames = {"organization_id", "usage_date"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** The UTC calendar day these counts cover. */
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    /** Number of metric points whose event time falls into the day. */
    @Column(name = "metrics_count", nullable = false)
    private long metricsCount;

    /** Number of log events whose event time falls into the day. */
    @Column(name = "logs_count", nullable = false)
    private long logsCount;

    /** Number of trace spans whose start time falls into the day. */
    @Column(name = "traces_count", nullable = false)
    private long tracesCount;
}
