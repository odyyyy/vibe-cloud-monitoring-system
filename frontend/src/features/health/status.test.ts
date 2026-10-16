import { describe, expect, it } from 'vitest'
import type { HealthCheckResult } from '../../api/healthchecks'
import { computeUptimePercent, presentationFor, resolveHealthStatus, summarizeHealthChecks } from './status'

function result(status: HealthCheckResult['status'], checkedAt: string, id = 1): HealthCheckResult {
  return { id, configId: 1, status, responseTimeMs: 10, errorMessage: null, checkedAt }
}

describe('resolveHealthStatus', () => {
  it('maps a missing status to UNKNOWN', () => {
    expect(resolveHealthStatus(null)).toBe('UNKNOWN')
    expect(resolveHealthStatus(undefined)).toBe('UNKNOWN')
  })

  it('keeps known statuses', () => {
    expect(resolveHealthStatus('DOWN')).toBe('DOWN')
  })
})

describe('presentationFor', () => {
  it('always provides a text label', () => {
    expect(presentationFor('UP').label).toBe('Up')
    expect(presentationFor(null).label).toBe('Unknown')
  })
})

describe('summarizeHealthChecks', () => {
  it('counts by latest result and treats missing results as unknown', () => {
    const summary = summarizeHealthChecks([
      { latest: result('UP', '2026-10-16T10:00:00Z', 1) },
      { latest: result('DOWN', '2026-10-16T10:00:00Z', 2) },
      { latest: result('TIMEOUT', '2026-10-16T10:00:00Z', 3) },
      { latest: null },
    ])
    expect(summary).toEqual({ up: 1, down: 1, timeout: 1, unknown: 1 })
  })
})

describe('computeUptimePercent', () => {
  const now = new Date('2026-10-16T12:00:00Z')

  it('returns null when there are no results', () => {
    expect(computeUptimePercent([], now)).toBeNull()
  })

  it('returns null when all results are outside the window', () => {
    const old = result('UP', '2026-10-10T12:00:00Z')
    expect(computeUptimePercent([old], now)).toBeNull()
  })

  it('computes the percentage from results inside the window', () => {
    const results = [
      result('UP', '2026-10-16T11:00:00Z', 1),
      result('UP', '2026-10-16T11:30:00Z', 2),
      result('DOWN', '2026-10-16T11:45:00Z', 3),
    ]
    expect(computeUptimePercent(results, now)).toBeCloseTo((2 / 3) * 100, 5)
  })

  it('ignores results outside the window when some are inside', () => {
    const results = [
      result('UP', '2026-10-16T11:00:00Z', 1),
      result('DOWN', '2026-10-10T11:00:00Z', 2),
    ]
    expect(computeUptimePercent(results, now)).toBe(100)
  })
})
