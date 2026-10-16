import type { Aggregation, MetricsQueryParams } from '../../api/metrics'
import type { TimeRange } from './timeRange'
import { metricsQuerySchema } from './schemas'

export interface MetricFilters {
  applicationId: number | null
  metricName: string
  range: TimeRange | null
  aggregation: Aggregation
  intervalMinutes: number
}

export const DEFAULT_METRIC_FILTERS: MetricFilters = {
  applicationId: null,
  metricName: '',
  range: null,
  aggregation: 'AVG',
  intervalMinutes: 5,
}

export interface ParsedMetricSearch {
  params: MetricsQueryParams | null
  error: string | null
}

function readParam(params: URLSearchParams, key: string): string | null {
  const value = params.get(key)
  return value === null || value === '' ? null : value
}

/** Reads committed metric filters from URL search params, validating the contract. */
export function parseMetricSearchParams(params: URLSearchParams): ParsedMetricSearch {
  const applicationId = readParam(params, 'applicationId')
  const metricName = readParam(params, 'metricName')
  const from = readParam(params, 'from')
  const to = readParam(params, 'to')
  const aggregation = readParam(params, 'aggregation')
  const intervalMinutes = readParam(params, 'intervalMinutes')

  if (!applicationId || !metricName || !from || !to) {
    return { params: null, error: null }
  }

  const result = metricsQuerySchema.safeParse({
    applicationId,
    metricName,
    from,
    to,
    aggregation: aggregation ?? 'AVG',
    intervalMinutes: intervalMinutes ?? '5',
  })

  if (!result.success) {
    return { params: null, error: result.error.issues[0]?.message ?? 'Invalid query parameters' }
  }

  return { params: result.data, error: null }
}

/** Populates the draft filter state from URL search params. */
export function filtersFromSearchParams(params: URLSearchParams, fallback: MetricFilters): MetricFilters {
  const parsed = parseMetricSearchParams(params)
  if (!parsed.params) {
    const applicationId = readParam(params, 'applicationId')
    return {
      ...fallback,
      applicationId: applicationId ? Number(applicationId) : null,
      metricName: readParam(params, 'metricName') ?? '',
    }
  }
  const committed = parsed.params
  return {
    applicationId: committed.applicationId,
    metricName: committed.metricName,
    range: { from: committed.from, to: committed.to },
    aggregation: committed.aggregation,
    intervalMinutes: committed.intervalMinutes,
  }
}

export function filtersToSearchParams(filters: MetricsQueryParams): Record<string, string> {
  return {
    applicationId: String(filters.applicationId),
    metricName: filters.metricName,
    from: filters.from,
    to: filters.to,
    aggregation: filters.aggregation,
    intervalMinutes: String(filters.intervalMinutes),
  }
}
