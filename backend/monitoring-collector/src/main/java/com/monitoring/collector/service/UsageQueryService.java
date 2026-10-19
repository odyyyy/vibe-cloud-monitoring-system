package com.monitoring.collector.service;

import com.monitoring.collector.config.UsageProperties;
import com.monitoring.collector.dto.response.CurrentMonthUsageResponse;
import com.monitoring.collector.dto.response.DailyUsagePointResponse;
import com.monitoring.collector.dto.response.UsageMeterResponse;
import com.monitoring.collector.entity.DailyUsage;
import com.monitoring.collector.repository.DailyUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read side of the usage data. Always scoped to an explicit organization id supplied by the
 * caller (derived from the authenticated tenant), never trusted from the request body.
 */
@Service
@RequiredArgsConstructor
public class UsageQueryService {

    /** Upper bound on the history window to keep the response and the query bounded. */
    static final int MAX_DAYS = 90;

    private final DailyUsageRepository dailyUsageRepository;
    private final UsageProperties usageProperties;

    /** Usage totals for the calendar month containing {@code month} (UTC), with plan limits. */
    @Transactional(readOnly = true)
    public CurrentMonthUsageResponse currentMonth(Long organizationId, YearMonth month) {
        LocalDate periodStart = month.atDay(1);
        LocalDate periodEnd = month.atEndOfMonth();

        List<DailyUsage> rows = dailyUsageRepository
                .findByOrganizationIdAndUsageDateBetweenOrderByUsageDateAsc(
                        organizationId, periodStart, periodEnd);

        long metrics = 0;
        long logs = 0;
        long traces = 0;
        for (DailyUsage row : rows) {
            metrics += row.getMetricsCount();
            logs += row.getLogsCount();
            traces += row.getTracesCount();
        }

        return CurrentMonthUsageResponse.builder()
                .organizationId(organizationId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .metrics(meter(metrics, usageProperties.getMetricsQuota()))
                .logs(meter(logs, usageProperties.getLogsQuota()))
                .traces(meter(traces, usageProperties.getTracesQuota()))
                .build();
    }

    /**
     * The last {@code days} days of usage ending at {@code endInclusive} (inclusive), oldest
     * first, with zero-filled gaps so the chart always has a continuous axis.
     */
    @Transactional(readOnly = true)
    public List<DailyUsagePointResponse> daily(Long organizationId, LocalDate endInclusive, int days) {
        int window = Math.min(Math.max(days, 1), MAX_DAYS);
        LocalDate start = endInclusive.minusDays(window - 1L);

        Map<LocalDate, DailyUsage> byDate = new HashMap<>();
        for (DailyUsage row : dailyUsageRepository
                .findByOrganizationIdAndUsageDateBetweenOrderByUsageDateAsc(
                        organizationId, start, endInclusive)) {
            byDate.put(row.getUsageDate(), row);
        }

        List<DailyUsagePointResponse> points = new ArrayList<>(window);
        for (LocalDate date = start; !date.isAfter(endInclusive); date = date.plusDays(1)) {
            DailyUsage row = byDate.get(date);
            points.add(DailyUsagePointResponse.builder()
                    .date(date)
                    .metrics(row == null ? 0 : row.getMetricsCount())
                    .logs(row == null ? 0 : row.getLogsCount())
                    .traces(row == null ? 0 : row.getTracesCount())
                    .build());
        }
        return points;
    }

    private UsageMeterResponse meter(long used, long limit) {
        double percentage = limit > 0
                ? Math.round(used * 1000.0 / limit) / 10.0
                : 0.0;
        return UsageMeterResponse.builder()
                .used(used)
                .limit(limit)
                .percentage(percentage)
                .build();
    }
}
