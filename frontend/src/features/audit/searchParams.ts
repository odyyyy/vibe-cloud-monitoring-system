import type { AuditEventsQueryParams } from '../../api/audit'
import { DEFAULT_AUDIT_PAGE_SIZE, auditQuerySchema } from './schemas'

export interface AuditFilters {
  action: string
  from: string
  to: string
  page: number
  size: number
}

export const DEFAULT_AUDIT_FILTERS: AuditFilters = {
  action: '',
  from: '',
  to: '',
  page: 0,
  size: DEFAULT_AUDIT_PAGE_SIZE,
}

export interface ParsedAuditSearch {
  params: AuditEventsQueryParams | null
  error: string | null
}

function readParam(params: URLSearchParams, key: string): string | null {
  const value = params.get(key)
  return value === null || value === '' ? null : value
}

/** Reads committed audit filters from URL search params, validating the contract. */
export function parseAuditSearchParams(params: URLSearchParams): ParsedAuditSearch {
  const result = auditQuerySchema.safeParse({
    action: readParam(params, 'action') ?? undefined,
    from: readParam(params, 'from') ?? undefined,
    to: readParam(params, 'to') ?? undefined,
    page: readParam(params, 'page') ?? '0',
    size: readParam(params, 'size') ?? String(DEFAULT_AUDIT_PAGE_SIZE),
  })

  if (!result.success) {
    return { params: null, error: result.error.issues[0]?.message ?? 'Invalid query parameters' }
  }

  return { params: result.data, error: null }
}

/** Populates the draft filter state from URL search params. */
export function filtersFromSearchParams(params: URLSearchParams, fallback: AuditFilters): AuditFilters {
  const parsed = parseAuditSearchParams(params)
  if (!parsed.params) {
    return {
      ...fallback,
      action: readParam(params, 'action') ?? '',
      from: readParam(params, 'from') ?? '',
      to: readParam(params, 'to') ?? '',
    }
  }

  const committed = parsed.params
  return {
    action: committed.action ?? '',
    from: committed.from ?? '',
    to: committed.to ?? '',
    page: committed.page,
    size: committed.size,
  }
}

/**
 * Serializes filters to URL params. `page` is only included when non-zero and `size` only when
 * it differs from the default, keeping the default view URL clean.
 */
export function filtersToSearchParams(filters: AuditEventsQueryParams): Record<string, string> {
  const serialized: Record<string, string> = {}
  if (filters.action) {
    serialized.action = filters.action
  }
  if (filters.from) {
    serialized.from = filters.from
  }
  if (filters.to) {
    serialized.to = filters.to
  }
  if (filters.page > 0) {
    serialized.page = String(filters.page)
  }
  if (filters.size !== DEFAULT_AUDIT_PAGE_SIZE) {
    serialized.size = String(filters.size)
  }
  return serialized
}
