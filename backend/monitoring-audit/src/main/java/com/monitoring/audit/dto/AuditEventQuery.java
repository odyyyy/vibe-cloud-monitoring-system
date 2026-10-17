package com.monitoring.audit.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * Filters and paging for the audit-event query endpoint.
 *
 * <p>All filters are optional. {@code from}/{@code to} form an inclusive UTC range; when both
 * are present the range must be positive and must not exceed {@link #MAX_RANGE_DAYS} days, so
 * a single request cannot scan an unbounded slice of the trail.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventQuery {

    public static final long MAX_RANGE_DAYS = 366;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_ACTION_LENGTH = 64;

    /** Optional action filter, for example {@code DASHBOARD_DELETED}. */
    @Size(max = MAX_ACTION_LENGTH, message = "action must be at most " + MAX_ACTION_LENGTH + " characters")
    private String action;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;

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
