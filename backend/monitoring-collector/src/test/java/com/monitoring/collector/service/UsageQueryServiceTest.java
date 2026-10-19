package com.monitoring.collector.service;

import com.monitoring.collector.config.UsageProperties;
import com.monitoring.collector.dto.response.CurrentMonthUsageResponse;
import com.monitoring.collector.dto.response.DailyUsagePointResponse;
import com.monitoring.collector.entity.DailyUsage;
import com.monitoring.collector.repository.DailyUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UsageQueryServiceTest {

    private DailyUsageRepository dailyUsageRepository;
    private UsageProperties usageProperties;
    private UsageQueryService service;

    @BeforeEach
    void setUp() {
        dailyUsageRepository = Mockito.mock(DailyUsageRepository.class);
        usageProperties = new UsageProperties();
        usageProperties.setMetricsQuota(1000L);
        usageProperties.setLogsQuota(500L);
        usageProperties.setTracesQuota(0L);
        service = new UsageQueryService(dailyUsageRepository, usageProperties);
    }

    @Test
    void currentMonth_sumsDaysWithinTheMonthAndReportsLimits() {
        when(dailyUsageRepository.findByOrganizationIdAndUsageDateBetweenOrderByUsageDateAsc(
                eq(1L), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(List.of(usage(LocalDate.of(2026, 10, 1), 300, 100, 0),
                        usage(LocalDate.of(2026, 10, 2), 400, 150, 0)));

        CurrentMonthUsageResponse response = service.currentMonth(1L, YearMonth.of(2026, 10));

        assertEquals(1L, response.getOrganizationId());
        assertEquals(LocalDate.of(2026, 10, 1), response.getPeriodStart());
        assertEquals(LocalDate.of(2026, 10, 31), response.getPeriodEnd());
        assertEquals(700L, response.getMetrics().getUsed());
        assertEquals(1000L, response.getMetrics().getLimit());
        assertEquals(70.0, response.getMetrics().getPercentage());
        assertEquals(250L, response.getLogs().getUsed());
        assertEquals(50.0, response.getLogs().getPercentage());
    }

    @Test
    void currentMonth_zeroLimit_yieldsZeroPercentage() {
        when(dailyUsageRepository.findByOrganizationIdAndUsageDateBetweenOrderByUsageDateAsc(
                any(), any(), any())).thenReturn(List.of(usage(LocalDate.of(2026, 10, 1), 10, 10, 10)));

        CurrentMonthUsageResponse response = service.currentMonth(1L, YearMonth.of(2026, 10));

        assertEquals(0.0, response.getTraces().getPercentage());
        assertEquals(10L, response.getTraces().getUsed());
    }

    @Test
    void daily_fillsGapsWithZerosAndReturnsAscendingWindow() {
        LocalDate end = LocalDate.of(2026, 10, 10);
        when(dailyUsageRepository.findByOrganizationIdAndUsageDateBetweenOrderByUsageDateAsc(
                eq(1L), eq(end.minusDays(4)), eq(end)))
                .thenReturn(List.of(usage(LocalDate.of(2026, 10, 8), 5, 6, 7)));

        List<DailyUsagePointResponse> points = service.daily(1L, end, 5);

        assertEquals(5, points.size());
        assertEquals(LocalDate.of(2026, 10, 6), points.get(0).getDate());
        assertEquals(0L, points.get(0).getMetrics());
        DailyUsagePointResponse eighth = points.get(2);
        assertEquals(LocalDate.of(2026, 10, 8), eighth.getDate());
        assertEquals(5L, eighth.getMetrics());
        assertEquals(6L, eighth.getLogs());
        assertEquals(7L, eighth.getTraces());
        assertEquals(end, points.get(4).getDate());
    }

    @Test
    void daily_capsRequestedWindowAtMaximum() {
        when(dailyUsageRepository.findByOrganizationIdAndUsageDateBetweenOrderByUsageDateAsc(
                any(), any(), any())).thenReturn(List.of());

        List<DailyUsagePointResponse> points = service.daily(1L, LocalDate.of(2026, 10, 10), 1000);

        assertEquals(UsageQueryService.MAX_DAYS, points.size());
    }

    @Test
    void daily_treatsNonPositiveWindowAsSingleDay() {
        when(dailyUsageRepository.findByOrganizationIdAndUsageDateBetweenOrderByUsageDateAsc(
                any(), any(), any())).thenReturn(List.of());

        List<DailyUsagePointResponse> points = service.daily(1L, LocalDate.of(2026, 10, 10), 0);

        assertEquals(1, points.size());
        assertEquals(LocalDate.of(2026, 10, 10), points.get(0).getDate());
    }

    private DailyUsage usage(LocalDate date, long metrics, long logs, long traces) {
        return DailyUsage.builder()
                .organizationId(1L)
                .usageDate(date)
                .metricsCount(metrics)
                .logsCount(logs)
                .tracesCount(traces)
                .build();
    }
}
