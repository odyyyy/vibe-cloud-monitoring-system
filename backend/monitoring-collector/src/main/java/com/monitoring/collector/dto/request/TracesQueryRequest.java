package com.monitoring.collector.dto.request;

import jakarta.validation.constraints.AssertTrue;
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
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Duration;
import java.time.Instant;

/**
 * Parameters for the recent-traces list.
 *
 * <p>{@code applicationId} is required (tenant scope), the time window is bounded by
 * {@link #MAX_RANGE_DAYS}, and the remaining filters (service, minimum duration, errors) are
 * optional. Paging is bounded by {@link #MAX_PAGE_SIZE}. Cross-field invariants turn into
 * HTTP 400 at the API boundary.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TracesQueryRequest {

    /** Maximum allowed query range in days; protects the database from unbounded scans. */
    public static final long MAX_RANGE_DAYS = 30;

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_SERVICE_NAME_LENGTH = 128;

    @NotNull(message = "applicationId is required")
    @Positive(message = "applicationId must be positive")
    private Long applicationId;

    /** Optional exact service-name filter; only traces containing such a span are returned. */
    @Size(max = MAX_SERVICE_NAME_LENGTH, message = "serviceName must be at most "
            + MAX_SERVICE_NAME_LENGTH + " characters")
    private String serviceName;

    /** Optional lower bound on the total trace duration in milliseconds. */
    @Min(value = 0, message = "minDurationMs must be greater than or equal to 0")
    private Long minDurationMs;

    /** When {@code true}, only traces with at least one ERROR span are returned. */
    @Builder.Default
    private boolean hasErrors = false;

    @NotNull(message = "from is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;

    @NotNull(message = "to is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;

    @Min(value = 0, message = "page must be greater than or equal to 0")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "size must be at least 1")
    @Max(value = MAX_PAGE_SIZE, message = "size must be at most " + MAX_PAGE_SIZE)
    @Builder.Default
    private int size = DEFAULT_PAGE_SIZE;

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
