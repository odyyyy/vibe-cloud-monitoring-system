import { describe, expect, it } from 'vitest'
import {
  DEFAULT_AUDIT_FILTERS,
  filtersFromSearchParams,
  filtersToSearchParams,
  parseAuditSearchParams,
} from './searchParams'
import { DEFAULT_AUDIT_PAGE_SIZE } from './schemas'

const from = '2024-05-01T11:00:00.000Z'
const to = '2024-05-01T12:00:00.000Z'

function buildParams(overrides: Record<string, string> = {}): URLSearchParams {
  return new URLSearchParams(overrides)
}

describe('parseAuditSearchParams', () => {
  it('produces a query with paging defaults when the URL is empty', () => {
    const parsed = parseAuditSearchParams(new URLSearchParams())
    expect(parsed.error).toBeNull()
    expect(parsed.params).toEqual({
      action: undefined,
      from: undefined,
      to: undefined,
      page: 0,
      size: DEFAULT_AUDIT_PAGE_SIZE,
    })
  })

  it('parses action, range and paging filters', () => {
    const parsed = parseAuditSearchParams(
      buildParams({ action: 'DASHBOARD_DELETED', from, to, page: '2', size: '25' }),
    )
    expect(parsed.error).toBeNull()
    expect(parsed.params).toEqual({
      action: 'DASHBOARD_DELETED',
      from,
      to,
      page: 2,
      size: 25,
    })
  })

  it('rejects an inverted range', () => {
    const parsed = parseAuditSearchParams(buildParams({ from: to, to: from }))
    expect(parsed.params).toBeNull()
    expect(parsed.error).toBeTruthy()
  })

  it('rejects ranges longer than 366 days', () => {
    const parsed = parseAuditSearchParams(
      buildParams({ from: '2024-01-01T00:00:00.000Z', to: '2025-05-01T00:00:00.000Z' }),
    )
    expect(parsed.params).toBeNull()
    expect(parsed.error).toContain('366 days')
  })

  it('rejects a page size above the maximum', () => {
    const parsed = parseAuditSearchParams(buildParams({ size: '500' }))
    expect(parsed.params).toBeNull()
    expect(parsed.error).toBeTruthy()
  })
})

describe('filtersFromSearchParams', () => {
  it('falls back to defaults when the URL is empty', () => {
    expect(filtersFromSearchParams(new URLSearchParams(), DEFAULT_AUDIT_FILTERS)).toEqual(
      DEFAULT_AUDIT_FILTERS,
    )
  })

  it('keeps partial filters for the draft even when the query is invalid', () => {
    const filters = filtersFromSearchParams(
      buildParams({ action: 'APPLICATION_DELETED', from, to: from }),
      DEFAULT_AUDIT_FILTERS,
    )
    expect(filters.action).toBe('APPLICATION_DELETED')
    expect(filters.from).toBe(from)
    expect(filters.to).toBe(from)
  })
})

describe('filtersToSearchParams', () => {
  it('omits default paging values', () => {
    expect(
      filtersToSearchParams({ page: 0, size: DEFAULT_AUDIT_PAGE_SIZE }),
    ).toEqual({})
  })

  it('serializes non-default filters and paging', () => {
    expect(
      filtersToSearchParams({ action: 'API_KEY_REGENERATED', from, to, page: 3, size: 25 }),
    ).toEqual({
      action: 'API_KEY_REGENERATED',
      from,
      to,
      page: '3',
      size: '25',
    })
  })
})
