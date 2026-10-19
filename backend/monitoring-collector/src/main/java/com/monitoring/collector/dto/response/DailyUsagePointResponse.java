package com.monitoring.collector.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One day of rolled-up usage, as returned to the history chart.
 *
 * <p>Days with no telemetry are returned explicitly with zero counts so the frontend can
 * render a continuous time axis without inferring gaps.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyUsagePointResponse {

    private LocalDate date;
    private long metrics;
    private long logs;
    private long traces;
}
