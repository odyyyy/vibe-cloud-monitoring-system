package com.monitoring.collector.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Duration;
import java.time.Instant;

/**
 * Parameters for a historical metrics query.
 *
 * <p>{@code from}/{@code to} define an inclusive range in UTC, {@code aggregation}
 * selects how points are folded within a bucket, and {@code intervalMinutes} is the
 * bucket width in minutes.
 *
 * <p>Cross-field invariants ({@code to > from} and a range of at most
 * {@link #MAX_RANGE_DAYS} days) are checked by {@link #isTimeRangeValid()} and turn
 * into HTTP 400 at the API boundary.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsQueryRequest {

    /** Maximum allowed query range in days. The limit protects the database from heavy scans. */
    public static final long MAX_RANGE_DAYS = 30;

    @NotNull(message = "applicationId is required")
    private Long applicationId;

    @NotBlank(message = "metricName is required")
    private String metricName;

    @NotNull(message = "from is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;

    @NotNull(message = "to is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;

    @NotNull(message = "aggregation is required")
    private MetricAggregation aggregation;

    @NotNull(message = "intervalMinutes is required")
    @Min(value = 1, message = "intervalMinutes must be greater than 0")
    private Integer intervalMinutes;

    @AssertTrue(message = "to must be after from and the range must not exceed "
            + MAX_RANGE_DAYS + " days")
    public boolean isTimeRangeValid() {
        if (from == null || to == null) {
            return true;
        }
        if (!to.isAfter(from)) {
            return false;
        }
        return Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) <= 0;
    }
}
