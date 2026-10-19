package com.monitoring.collector.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Current-period (calendar month, UTC) usage for one organization, with plan limits.
 *
 * <p>{@code periodStart}/{@code periodEnd} are inclusive day boundaries of the month and let
 * the frontend label the period without duplicating date math.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentMonthUsageResponse {

    private Long organizationId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private UsageMeterResponse metrics;
    private UsageMeterResponse logs;
    private UsageMeterResponse traces;
}
