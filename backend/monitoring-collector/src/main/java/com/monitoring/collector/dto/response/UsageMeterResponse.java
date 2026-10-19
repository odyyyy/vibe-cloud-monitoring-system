package com.monitoring.collector.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single usage meter: how much of a quota has been consumed in the current period.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageMeterResponse {

    /** Items consumed so far in the period. */
    private long used;

    /** Plan limit for the period; never negative. */
    private long limit;

    /**
     * Fraction of the limit consumed, as a percentage rounded to one decimal.
     * {@code 0.0} when the limit is zero.
     */
    private double percentage;
}
