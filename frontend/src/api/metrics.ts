import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiDownload, apiRequest, type DownloadResponse, type ExportFormat } from './client'

export type Aggregation = 'AVG' | 'MAX' | 'MIN' | 'SUM'

export interface MetricsQueryParams {
  applicationId: number
  metricName: string
  from: string
  to: string
  aggregation: Aggregation
  intervalMinutes: number
  groupByTag?: string
}

export interface AggregatedMetricPoint {
  timestamp: string
  value: number
  /** Tag value when the query is grouped, otherwise undefined. */
  group?: string | null
}

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getMetricsQuery(params: MetricsQueryParams): Promise<AggregatedMetricPoint[]> {
  return apiRequest<AggregatedMetricPoint[]>(
    `/v1/metrics/query?${buildMetricsSearch(params).toString()}`,
    { token: authToken() },
  )
}

export function exportMetrics(params: MetricsQueryParams, format: ExportFormat): Promise<DownloadResponse> {
  const search = buildMetricsSearch(params)
  search.set('format', format)
  return apiDownload(`/v1/metrics/query?${search.toString()}`, { token: authToken() })
}

function buildMetricsSearch(params: MetricsQueryParams): URLSearchParams {
  const search = new URLSearchParams({
    applicationId: String(params.applicationId),
    metricName: params.metricName,
    from: params.from,
    to: params.to,
    aggregation: params.aggregation,
    intervalMinutes: String(params.intervalMinutes),
  })
  if (params.groupByTag) {
    search.set('groupByTag', params.groupByTag)
  }
  return search
}

export const metricsQueryKey = (params: MetricsQueryParams | null) =>
  [
    'metrics',
    'query',
    params?.applicationId ?? null,
    params?.metricName ?? null,
    params?.from ?? null,
    params?.to ?? null,
    params?.aggregation ?? null,
    params?.intervalMinutes ?? null,
    params?.groupByTag ?? null,
  ] as const

export function useMetricsQuery(params: MetricsQueryParams | null, enabled: boolean) {
  return useQuery({
    queryKey: metricsQueryKey(params),
    queryFn: () => getMetricsQuery(params as MetricsQueryParams),
    enabled: enabled && params !== null,
  })
}

export function getMetricNames(applicationId: number): Promise<string[]> {
  const search = new URLSearchParams({ applicationId: String(applicationId) })
  return apiRequest<string[]>(`/v1/metrics/names?${search.toString()}`, { token: authToken() })
}

export const metricNamesQueryKey = (applicationId: number | null) =>
  ['metrics', 'names', applicationId ?? null] as const

export function useMetricNamesQuery(applicationId: number | null) {
  return useQuery({
    queryKey: metricNamesQueryKey(applicationId),
    queryFn: () => getMetricNames(applicationId as number),
    enabled: applicationId !== null,
    staleTime: 60_000,
  })
}
