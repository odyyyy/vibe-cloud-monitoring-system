import { describe, expect, it } from 'vitest'
import { NAV_ITEMS, PLATFORM_HEALTH_NAV_ITEM, isPathActive } from './nav'

describe('NAV_ITEMS', () => {
  it('exposes the expected sections in order', () => {
    expect(NAV_ITEMS.map((item) => item.label)).toEqual([
      'Overview',
      'Applications',
      'Agents',
      'Metrics',
      'Logs',
      'Health',
      'Status pages',
      'Alerts',
      'Incidents',
      'Maintenance',
      'Dashboards',
      'Audit log',
      'Organization',
      'Usage',
      'Settings',
    ])
  })

  it('routes every item under /app', () => {
    for (const item of NAV_ITEMS) {
      expect(item.to.startsWith('/app/')).toBe(true)
    }
  })
})

describe('PLATFORM_HEALTH_NAV_ITEM', () => {
  it('points to the platform administration route and is not part of the tenant navigation', () => {
    expect(PLATFORM_HEALTH_NAV_ITEM.to).toBe('/admin/platform-health')
    expect(PLATFORM_HEALTH_NAV_ITEM.label).toBe('Platform Health')
    expect(NAV_ITEMS).not.toContainEqual(PLATFORM_HEALTH_NAV_ITEM)
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
