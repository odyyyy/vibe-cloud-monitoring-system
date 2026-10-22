import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiDownload, apiRequest, type DownloadResponse, type ExportFormat } from './client'

export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

/** Fields the backend may aggregate for the facet sidebar. */
export const LOG_FACET_FIELDS = ['level', 'host', 'service', 'k8s.namespace'] as const

export type LogFacetField = (typeof LOG_FACET_FIELDS)[number]

export interface LogsQueryParams {
  applicationId: number
  level?: LogLevel
  from: string
  to: string
  searchText?: string
  /** Exact-match filter on the {@code host} member of the log context document. */
  host?: string
  /** Exact-match filter on the {@code service} member of the log context document. */
  service?: string
  /** Exact-match filter on the {@code k8s.namespace} member of the log context document. */
  namespace?: string
  page: number
  size: number
}

export interface LogFacetValue {
  value: string
  count: number
}

export interface LogFacetsResponse {
  /** Top values per requested field, ordered by descending count. */
  facets: Record<string, LogFacetValue[]>
  /** True when the counts were computed over the most recent rows only. */
  approximate: boolean
  /** The number of rows the aggregation scanned at most. */
  scanLimit: number
}

export interface LogEvent {
  id: number
  applicationId: number
  timestamp: string
  level: LogLevel
  message: string
  context?: string | null
}

export interface LogsPageResponse {
  content: LogEvent[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getLogs(params: LogsQueryParams): Promise<LogsPageResponse> {
  return apiRequest<LogsPageResponse>(`/v1/logs?${buildLogsSearch(params).toString()}`, { token: authToken() })
}

export function exportLogs(params: LogsQueryParams, format: ExportFormat): Promise<DownloadResponse> {
  const search = buildLogsSearch(params)
  search.set('format', format)
  return apiDownload(`/v1/logs?${search.toString()}`, { token: authToken() })
}

function buildLogsSearch(params: LogsQueryParams): URLSearchParams {
  const search = new URLSearchParams({
    applicationId: String(params.applicationId),
    from: params.from,
    to: params.to,
    page: String(params.page),
    size: String(params.size),
  })
  if (params.level) {
    search.set('level', params.level)
  }
  if (params.searchText) {
    search.set('searchText', params.searchText)
  }
  if (params.host) {
    search.set('host', params.host)
  }
  if (params.service) {
    search.set('service', params.service)
  }
  if (params.namespace) {
    search.set('namespace', params.namespace)
  }
  return search
}

export function getLogFacets(
  params: LogsQueryParams,
  fields: readonly LogFacetField[],
  signal?: AbortSignal,
): Promise<LogFacetsResponse> {
  const search = buildLogsSearch(params)
  // Paging is meaningless for the aggregation; the endpoint ignores it.
  search.delete('page')
  search.delete('size')
  search.set('fields', fields.join(','))
  return apiRequest<LogFacetsResponse>(`/v1/logs/facets?${search.toString()}`, {
    token: authToken(),
    signal,
  })
}

export const logFacetsQueryKey = (params: LogsQueryParams | null, fields: readonly LogFacetField[]) =>
  [
    'logs',
    'facets',
    params?.applicationId ?? null,
    params?.level ?? null,
    params?.from ?? null,
    params?.to ?? null,
    params?.searchText ?? null,
    params?.host ?? null,
    params?.service ?? null,
    params?.namespace ?? null,
    fields.join(','),
  ] as const

/**
 * Loads the facet counts in parallel with the result list. A changed query cancels the previous
 * request so a stale response can never replace a newer one.
 */
export function useLogFacetsQuery(
  params: LogsQueryParams | null,
  fields: readonly LogFacetField[],
  enabled: boolean,
) {
  return useQuery({
    queryKey: logFacetsQueryKey(params, fields),
    queryFn: ({ signal }) => getLogFacets(params as LogsQueryParams, fields, signal),
    enabled: enabled && params !== null,
    keepPreviousData: true,
    retry: false,
  })
}

export const logsQueryKey = (params: LogsQueryParams | null) =>
  [
    'logs',
    'query',
    params?.applicationId ?? null,
    params?.level ?? null,
    params?.from ?? null,
    params?.to ?? null,
    params?.searchText ?? null,
    params?.host ?? null,
    params?.service ?? null,
    params?.namespace ?? null,
    params?.page ?? null,
    params?.size ?? null,
  ] as const

export function useLogsQuery(params: LogsQueryParams | null, enabled: boolean) {
  return useQuery({
    queryKey: logsQueryKey(params),
    queryFn: () => getLogs(params as LogsQueryParams),
    enabled: enabled && params !== null,
    keepPreviousData: true,
  })
}
