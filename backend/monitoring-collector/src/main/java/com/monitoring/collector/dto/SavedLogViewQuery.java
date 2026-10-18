package com.monitoring.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.monitoring.collector.entity.LogLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The validated filter set stored inside a saved log view.
 *
 * <p>This is the only shape persisted in {@code saved_log_views.query_params}; it is validated on
 * every write. Cross-field rules use {@link AssertTrue} (which Jackson ignores so the flag does not
 * leak into API responses). Input is data only: text is length-bounded and must not contain angle
 * brackets so no markup/expression can be smuggled in.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedLogViewQuery {

    public static final int MAX_SEARCH_TEXT_LENGTH = 200;
    public static final long MAX_RANGE_DAYS = 30;

    @NotNull(message = "applicationId is required")
    @Positive(message = "applicationId must be positive")
    private Long applicationId;

    private LogLevel level;

    @Size(max = MAX_SEARCH_TEXT_LENGTH, message = "searchText must be at most 200 characters")
    private String searchText;

    @NotNull(message = "timeRange is required")
    @Valid
    private TimeRange timeRange;

    @JsonIgnore
    @AssertTrue(message = "searchText must not contain '<' or '>'")
    public boolean isSearchTextSafe() {
        return searchText == null || (searchText.indexOf('<') < 0 && searchText.indexOf('>') < 0);
    }

    /** Absolute UTC bounds for the log query. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimeRange {

        @NotNull(message = "timeRange.from is required")
        private Instant from;

        @NotNull(message = "timeRange.to is required")
        private Instant to;

        @JsonIgnore
        @AssertTrue(message = "timeRange.to must be after timeRange.from and the range must not exceed "
                + MAX_RANGE_DAYS + " days")
        public boolean isTimeRangeValid() {
            if (from == null || to == null) {
                return true;
            }
            return to.isAfter(from) && !from.plus(MAX_RANGE_DAYS, ChronoUnit.DAYS).isBefore(to);
        }
    }
}
