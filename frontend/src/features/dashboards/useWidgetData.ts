import { useQuery } from '@tanstack/react-query'
import type { LogLevel, LogsPageResponse } from '../../api/logs'
import { getLogs } from '../../api/logs'
import type { AggregatedMetricPoint } from '../../api/metrics'
import { getMetricsQuery } from '../../api/metrics'
import { getSharedWidgetLogs, getSharedWidgetMetrics } from '../../api/dashboards'
import { resolvePresetRange } from '../metrics/timeRange'
import type { GaugeConfig, LogTableConfig, MetricChartConfig } from './schemas'

const WIDGET_LOGS_PAGE_SIZE = 10

function relativeRange(hours: number) {
  return resolvePresetRange({ id: 'relative', label: 'relative', hours })
}

/**
 * Data hook for a METRIC_CHART widget. The absolute time window is resolved inside
 * the query function (not in render) so the query key stays stable and polling keeps
 * requesting the latest window instead of spawning a new request on every render.
 */
export function useMetricChartWidget(
  widgetId: number,
  config: MetricChartConfig,
  refetchInterval: number,
  shareToken?: string,
) {
  const { applicationId, metricName, aggregation, intervalMinutes, timeRangeHours, groupByTag } = config

  return useQuery<AggregatedMetricPoint[]>({
    queryKey: [
      'dashboard-widget',
      'metric',
      widgetId,
      applicationId,
      metricName,
      aggregation,
      intervalMinutes,
      timeRangeHours,
      groupByTag ?? null,
      shareToken ?? null,
    ] as const,
    queryFn: () => {
      const { from, to } = relativeRange(timeRangeHours)
      if (shareToken) {
        return getSharedWidgetMetrics(shareToken, widgetId, { from, to })
      }
      return getMetricsQuery({ applicationId, metricName, from, to, aggregation, intervalMinutes, groupByTag })
    },
    refetchInterval,
  })
}

/**
 * Data hook for a GAUGE widget: the same time-series query with a small step, reduced to the
 * most recent non-null point by the renderer.
 */
export function useGaugeWidget(
  widgetId: number,
  config: GaugeConfig,
  refetchInterval: number,
  shareToken?: string,
) {
  const { applicationId, metricName, timeRangeHours } = config

  return useQuery<AggregatedMetricPoint[]>({
    queryKey: [
      'dashboard-widget',
      'gauge',
      widgetId,
      applicationId,
      metricName,
      timeRangeHours,
      shareToken ?? null,
    ] as const,
    queryFn: () => {
      const { from, to } = relativeRange(timeRangeHours)
      if (shareToken) {
        return getSharedWidgetMetrics(shareToken, widgetId, { from, to })
      }
      return getMetricsQuery({
        applicationId,
        metricName,
        from,
        to,
        aggregation: 'MAX',
        intervalMinutes: 1,
      })
    },
    refetchInterval,
  })
}

/** Data hook for a LOG_TABLE widget, mirroring {@link useMetricChartWidget}. */
export function useLogTableWidget(
  widgetId: number,
  config: LogTableConfig,
  refetchInterval: number,
  shareToken?: string,
) {
  const { applicationId, level, searchText, timeRangeHours } = config

  return useQuery<LogsPageResponse>({
    queryKey: ['dashboard-widget', 'logs', widgetId, applicationId, level ?? null, searchText ?? null, timeRangeHours, shareToken ?? null] as const,
    queryFn: () => {
      const { from, to } = relativeRange(timeRangeHours)
      if (shareToken) {
        return getSharedWidgetLogs(shareToken, widgetId, {
          from,
          to,
          page: 0,
          size: WIDGET_LOGS_PAGE_SIZE,
        })
      }
      return getLogs({
        applicationId,
        level: level as LogLevel | undefined,
        from,
        to,
        searchText: searchText || undefined,
        page: 0,
        size: WIDGET_LOGS_PAGE_SIZE,
      })
    },
    refetchInterval,
  })
}
