import { describe, expect, it } from 'vitest'
import type { AlertIncident } from '../../api/alerts'
import { logExplorerHref } from './incidentLinks'

function logIncident(overrides: Partial<AlertIncident> = {}): AlertIncident {
  return {
    id: 1,
    ruleId: 2,
    ruleName: 'Checkout timeouts',
    ruleType: 'LOG',
    applicationId: 7,
    logLevel: 'ERROR',
    logQuery: 'timeout "db"',
    windowMinutes: 5,
    comparison: 'GREATER_OR_EQUAL',
    countThreshold: 10,
    status: 'FIRING',
    triggeredAt: '2026-10-17T10:00:00Z',
    acknowledgedBy: null,
    acknowledgedByName: null,
    acknowledgedAt: null,
    resolvedBy: null,
    resolvedByName: null,
    resolvedAt: null,
    notes: null,
    ...overrides,
  }
}

describe('logExplorerHref', () => {
  it('builds a link to the incident window and log filters', () => {
    const href = logExplorerHref(logIncident())

    expect(href).not.toBeNull()
    const url = new URL(href!, 'https://app.example.com')
    expect(url.pathname).toBe('/app/logs')
    expect(url.searchParams.get('applicationId')).toBe('7')
    expect(url.searchParams.get('from')).toBe('2026-10-17T09:55:00.000Z')
    expect(url.searchParams.get('to')).toBe('2026-10-17T10:00:00.000Z')
    expect(url.searchParams.get('level')).toBe('ERROR')
    expect(url.searchParams.get('searchText')).toBe('timeout "db"')
  })

  it('omits filters that are not configured', () => {
    const href = logExplorerHref(logIncident({ logLevel: null, logQuery: null }))

    expect(href).not.toBeNull()
    const url = new URL(href!, 'https://app.example.com')
    expect(url.searchParams.has('level')).toBe(false)
    expect(url.searchParams.has('searchText')).toBe(false)
  })

  it('returns null for non-log incidents', () => {
    expect(logExplorerHref(logIncident({ ruleType: 'METRIC_THRESHOLD' }))).toBeNull()
  })

  it('returns null when the application or window is missing', () => {
    expect(logExplorerHref(logIncident({ applicationId: null }))).toBeNull()
    expect(logExplorerHref(logIncident({ windowMinutes: null }))).toBeNull()
  })

  it('returns null for an invalid trigger timestamp', () => {
    expect(logExplorerHref(logIncident({ triggeredAt: 'not-a-date' }))).toBeNull()
  })
})
