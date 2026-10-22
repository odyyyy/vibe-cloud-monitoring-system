package com.monitoring.collector.dto.request;

import com.monitoring.collector.entity.LogLevel;
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
 * Parameters for a historical log query.
 *
 * <p>{@code from}/{@code to} define an inclusive UTC range, {@code level} and
 * {@code searchText} are optional filters, and paging is bounded by {@link #MAX_PAGE_SIZE}.
 * The optional {@code host}, {@code service} and {@code namespace} filters are exact matches on
 * members of the JSON context document and are used by the facet sidebar to narrow the result
 * list down to a single value.
 * Cross-field invariants turn into HTTP 400 at the API boundary.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogsQueryRequest {

    /** Maximum allowed query range in days; protects the database from unbounded scans. */
    public static final long MAX_RANGE_DAYS = 30;

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_SEARCH_TEXT_LENGTH = 200;

    /**
     * Maximum length of an exact-match facet filter value. The agent truncates the reserved
     * context values to 256 characters, so no stored value is longer than that.
     */
    public static final int MAX_FACET_VALUE_LENGTH = 256;

    @NotNull(message = "applicationId is required")
    @Positive(message = "applicationId must be positive")
    private Long applicationId;

    /** Optional severity filter; {@code null} means "all levels". */
    private LogLevel level;

    @NotNull(message = "from is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;

    @NotNull(message = "to is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;

    /** Optional literal substring match against the message (case-insensitive). */
    @Size(max = MAX_SEARCH_TEXT_LENGTH, message = "searchText must be at most "
            + MAX_SEARCH_TEXT_LENGTH + " characters")
    private String searchText;

    /** Optional exact match on the {@code host} member of the JSON context document. */
    @Size(max = MAX_FACET_VALUE_LENGTH, message = "host must be at most "
            + MAX_FACET_VALUE_LENGTH + " characters")
    private String host;

    /** Optional exact match on the {@code service} member of the JSON context document. */
    @Size(max = MAX_FACET_VALUE_LENGTH, message = "service must be at most "
            + MAX_FACET_VALUE_LENGTH + " characters")
    private String service;

    /** Optional exact match on the {@code k8s.namespace} member of the JSON context document. */
    @Size(max = MAX_FACET_VALUE_LENGTH, message = "namespace must be at most "
            + MAX_FACET_VALUE_LENGTH + " characters")
    private String namespace;

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
