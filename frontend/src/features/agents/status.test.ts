import { describe, expect, it } from 'vitest'
import { AGENT_ONLINE_THRESHOLD_MS, presentationFor, resolveAgentStatus } from './status'

const NOW = new Date('2026-10-18T12:00:00Z')

describe('resolveAgentStatus', () => {
  it('is ONLINE when the last ingest is inside the freshness window', () => {
    const seen = new Date(NOW.getTime() - 60_000).toISOString()
    expect(resolveAgentStatus(seen, NOW)).toBe('ONLINE')
  })

  it('is OFFLINE when the last ingest is older than the window', () => {
    const seen = new Date(NOW.getTime() - AGENT_ONLINE_THRESHOLD_MS - 1).toISOString()
    expect(resolveAgentStatus(seen, NOW)).toBe('OFFLINE')
  })

  it('treats the exact threshold boundary as OFFLINE', () => {
    const seen = new Date(NOW.getTime() - AGENT_ONLINE_THRESHOLD_MS).toISOString()
    expect(resolveAgentStatus(seen, NOW)).toBe('OFFLINE')
  })

  it('treats a future timestamp (clock skew) as ONLINE', () => {
    const seen = new Date(NOW.getTime() + 30_000).toISOString()
    expect(resolveAgentStatus(seen, NOW)).toBe('ONLINE')
  })

  it('is UNKNOWN for a missing or unparseable timestamp', () => {
    expect(resolveAgentStatus(null, NOW)).toBe('UNKNOWN')
    expect(resolveAgentStatus(undefined, NOW)).toBe('UNKNOWN')
    expect(resolveAgentStatus('not-a-date', NOW)).toBe('UNKNOWN')
  })
})

describe('presentationFor', () => {
  it('always provides a text label', () => {
    expect(presentationFor('ONLINE').label).toBe('Online')
    expect(presentationFor('OFFLINE').label).toBe('Offline')
    expect(presentationFor('UNKNOWN').label).toBe('Unknown')
  })
})
