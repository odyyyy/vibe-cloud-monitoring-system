package com.monitoring.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Aggregated summary of one trace for the recent-traces list: its time window, total
 * duration, span count and whether any span failed.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TraceSummaryResponse {

    private String traceId;
    private Instant startTime;
    private long durationMs;
    private long spanCount;
    private boolean hasError;
}
