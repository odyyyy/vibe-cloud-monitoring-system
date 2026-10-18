package com.monitoring.collector.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * A full trace: its identity, application, overall time window and every span (ordered by
 * start time) needed to render the waterfall.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TraceDetailResponse {

    private String traceId;
    private Long applicationId;
    private Instant startTime;
    private long durationMs;
    private boolean hasError;
    private List<TraceSpanResponse> spans;
}
