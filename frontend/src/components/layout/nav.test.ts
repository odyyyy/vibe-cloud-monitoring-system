import { describe, expect, it } from 'vitest'
import { NAV_ITEMS, isPathActive } from './nav'

describe('NAV_ITEMS', () => {
  it('exposes the expected sections in order', () => {
    expect(NAV_ITEMS.map((item) => item.label)).toEqual([
      'Overview',
      'Applications',
      'Agents',
      'Metrics',
      'Logs',
      'Health',
      'Alerts',
      'Incidents',
      'Dashboards',
      'Audit log',
      'Organization',
      'Settings',
    ])
  })

  it('routes every item under /app', () => {
    for (const item of NAV_ITEMS) {
      expect(item.to.startsWith('/app/')).toBe(true)
    }
  })
})

describe('isPathActive', () => {
  it('matches the exact route', () => {
    expect(isPathActive('/app/settings', '/app/settings')).toBe(true)
  })

  it('matches nested routes', () => {
    expect(isPathActive('/app/settings/profile', '/app/settings')).toBe(true)
  })

  it('does not match a different section', () => {
    expect(isPathActive('/app/settings', '/app/metrics')).toBe(false)
  })

  it('does not treat a shared prefix as an active section', () => {
    expect(isPathActive('/app/metrics-archive', '/app/metrics')).toBe(false)
  })
})
