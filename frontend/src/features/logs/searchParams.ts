import type { LogLevel, LogsQueryParams } from '../../api/logs'
import type { TimeRange } from '../metrics/timeRange'
import { logsQuerySchema, DEFAULT_LOG_PAGE_SIZE } from './schemas'

export type LogLevelFilter = LogLevel | 'ALL'

export interface LogFilters {
  applicationId: number | null
  level: LogLevelFilter
  range: TimeRange | null
  searchText: string
  page: number
  size: number
}

export const DEFAULT_LOG_FILTERS: LogFilters = {
  applicationId: null,
  level: 'ALL',
  range: null,
  searchText: '',
  page: 0,
  size: DEFAULT_LOG_PAGE_SIZE,
}

export interface ParsedLogSearch {
  params: LogsQueryParams | null
  error: string | null
}

function readParam(params: URLSearchParams, key: string): string | null {
  const value = params.get(key)
  return value === null || value === '' ? null : value
}

/** Reads committed log filters from URL search params, validating the contract. */
export function parseLogSearchParams(params: URLSearchParams): ParsedLogSearch {
  const applicationId = readParam(params, 'applicationId')
  const from = readParam(params, 'from')
  const to = readParam(params, 'to')

  if (!applicationId || !from || !to) {
    return { params: null, error: null }
  }

  const level = readParam(params, 'level')
  const searchText = readParam(params, 'searchText')

  const result = logsQuerySchema.safeParse({
    applicationId,
    level: level ?? undefined,
    from,
    to,
    searchText: searchText ?? undefined,
    host: readParam(params, 'host') ?? undefined,
    service: readParam(params, 'service') ?? undefined,
    namespace: readParam(params, 'namespace') ?? undefined,
    page: readParam(params, 'page') ?? '0',
    size: readParam(params, 'size') ?? String(DEFAULT_LOG_PAGE_SIZE),
  })

  if (!result.success) {
    return { params: null, error: result.error.issues[0]?.message ?? 'Invalid query parameters' }
  }

  return { params: result.data, error: null }
}

/** Populates the draft filter state from URL search params. */
export function filtersFromSearchParams(params: URLSearchParams, fallback: LogFilters): LogFilters {
  const parsed = parseLogSearchParams(params)
  if (!parsed.params) {
    const applicationId = readParam(params, 'applicationId')
    const level = readParam(params, 'level')
    return {
      ...fallback,
      applicationId: applicationId ? Number(applicationId) : null,
      level: isLogLevel(level) ? level : fallback.level,
      searchText: readParam(params, 'searchText') ?? '',
    }
  }

  const committed = parsed.params
  return {
    applicationId: committed.applicationId,
    level: committed.level ?? 'ALL',
    range: { from: committed.from, to: committed.to },
    searchText: committed.searchText ?? '',
    page: committed.page,
    size: committed.size,
  }
}

/**
 * Serializes filters to URL params. `page` is only included when non-zero and
 * `size` only when it differs from the default, keeping the default view URL clean.
 */
export function filtersToSearchParams(filters: LogsQueryParams): Record<string, string> {
  const serialized: Record<string, string> = {
    applicationId: String(filters.applicationId),
    from: filters.from,
    to: filters.to,
  }
  if (filters.level) {
    serialized.level = filters.level
  }
  if (filters.searchText) {
    serialized.searchText = filters.searchText
  }
  if (filters.host) {
    serialized.host = filters.host
  }
  if (filters.service) {
    serialized.service = filters.service
  }
  if (filters.namespace) {
    serialized.namespace = filters.namespace
  }
  if (filters.page > 0) {
    serialized.page = String(filters.page)
  }
  if (filters.size !== DEFAULT_LOG_PAGE_SIZE) {
    serialized.size = String(filters.size)
  }
  return serialized
}

function isLogLevel(value: string | null): value is LogLevel {
  return value === 'DEBUG' || value === 'INFO' || value === 'WARN' || value === 'ERROR'
}
