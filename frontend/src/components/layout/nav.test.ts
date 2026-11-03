import { describe, expect, it } from 'vitest'
import {
  FOOTER_NAV_ITEMS,
  NAV_GROUPS,
  NAV_ITEMS,
  PLATFORM_HEALTH_NAV_ITEM,
  isPathActive,
} from './nav'

describe('NAV_ITEMS', () => {
  it('exposes every grouped item and the settings footer', () => {
    expect(NAV_ITEMS.map((item) => item.label)).toEqual([
      'Overview',
      'Dashboards',
      'Metrics',
      'Logs',
      'Log pipelines',
      'Health',
      'Status pages',
      'Alerts',
      'Incidents',
      'Maintenance',
      'Delivery log',
      'Reports',
      'Applications',
      'Agents',
      'Organization',
      'Usage',
      'Audit log',
      'Settings',
    ])
  })

  it('routes every item under /app', () => {
    for (const item of NAV_ITEMS) {
      expect(item.to.startsWith('/app/')).toBe(true)
    }
  })

  it('keeps navigation grouped so the sidebar stays scannable', () => {
    expect(NAV_GROUPS.map((group) => group.label)).toEqual([
      'Workspace',
      'Observability',
      'Alerting',
      'Administration',
    ])
    expect(NAV_GROUPS.flatMap((group) => group.items).every((item) => NAV_ITEMS.includes(item))).toBe(true)
  })

  it('renders Settings and the setup guide in the footer instead of the main groups', () => {
    expect(FOOTER_NAV_ITEMS.map((item) => item.label)).toEqual(['Settings'])
    expect(NAV_GROUPS.some((group) => group.items.some((item) => item.to === '/app/settings'))).toBe(false)
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
