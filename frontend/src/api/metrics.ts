import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export type Aggregation = 'AVG' | 'MAX' | 'MIN' | 'SUM'

export interface MetricsQueryParams {
  applicationId: number
  metricName: string
  from: string
  to: string
  aggregation: Aggregation
  intervalMinutes: number
}

export interface AggregatedMetricPoint {
  timestamp: string
  value: number
}

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getMetricsQuery(params: MetricsQueryParams): Promise<AggregatedMetricPoint[]> {
  const search = new URLSearchParams({
    applicationId: String(params.applicationId),
    metricName: params.metricName,
    from: params.from,
    to: params.to,
    aggregation: params.aggregation,
    intervalMinutes: String(params.intervalMinutes),
  })

  return apiRequest<AggregatedMetricPoint[]>(`/v1/metrics/query?${search.toString()}`, { token: authToken() })
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
