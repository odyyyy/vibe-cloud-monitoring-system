import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiDownload, apiRequest, type DownloadResponse, type ExportFormat } from './client'

export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

export interface LogsQueryParams {
  applicationId: number
  level?: LogLevel
  from: string
  to: string
  searchText?: string
  page: number
  size: number
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
  return search
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
