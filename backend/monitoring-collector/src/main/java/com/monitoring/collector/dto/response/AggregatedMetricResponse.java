package com.monitoring.collector.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single aggregated time-series point.
 * {@code timestamp} is the start of the aggregation interval (UTC).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AggregatedMetricResponse {

    private Instant timestamp;

    private Double value;

    /** Tag value when the query is grouped by a tag, otherwise {@code null}. */
    private String group;
}
