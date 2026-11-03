import { Search } from 'lucide-react'
import { describe, expect, it } from 'vitest'
import { NAV_ITEMS } from '../../components/layout/nav'
import { scoreEntry, rankEntries } from './matching'
import {
  accessibleEntries,
  canAccessEntry,
  FEATURE_ENTRIES,
  featureEntryForNavItem,
  PALETTE_ENTRIES,
} from './registry'

function entry(id: string) {
  const found = PALETTE_ENTRIES.find((candidate) => candidate.id === id)
  if (!found) {
    throw new Error(`Missing palette entry ${id}`)
  }
  return found
}

describe('palette access rules', () => {
  it('shows read-only pages to viewers', () => {
    const entries = accessibleEntries('VIEWER', 'USER')
    expect(entries.some((candidate) => candidate.id === 'page-logs')).toBe(true)
    expect(entries.some((candidate) => candidate.id === 'feature-retention')).toBe(true)
  })

  it('hides admin-only features from viewers', () => {
    expect(canAccessEntry(entry('feature-members'), 'VIEWER', 'USER')).toBe(false)
    expect(canAccessEntry(entry('feature-notification-channels'), 'VIEWER', 'USER')).toBe(false)
    expect(canAccessEntry(entry('feature-members'), null, 'USER')).toBe(false)
  })

  it('shows admin-only features to admins and owners', () => {
    expect(canAccessEntry(entry('feature-members'), 'ADMIN', 'USER')).toBe(true)
    expect(canAccessEntry(entry('feature-members'), 'OWNER', 'USER')).toBe(true)
  })

  it('keeps platform health separate from organization roles', () => {
    expect(canAccessEntry(entry('page-platform-health'), 'OWNER', 'USER')).toBe(false)
    expect(canAccessEntry(entry('page-platform-health'), 'VIEWER', 'PLATFORM_ADMIN')).toBe(true)
    expect(accessibleEntries('VIEWER', 'PLATFORM_ADMIN').some((candidate) => candidate.id === 'page-platform-health')).toBe(true)
  })

  it('does not advertise features without a page', () => {
    expect(FEATURE_ENTRIES.some((candidate) => /sso|trace/i.test(candidate.title))).toBe(false)
  })

  it('has unique ids and only internal routes', () => {
    expect(new Set(PALETTE_ENTRIES.map((candidate) => candidate.id)).size).toBe(PALETTE_ENTRIES.length)
    expect(FEATURE_ENTRIES.every((candidate) => /^\/(app|admin)\//.test(candidate.route))).toBe(true)
  })
})

describe('automatic sidebar indexing', () => {
  it('indexes every sidebar item without a hand-written palette entry', () => {
    const indexedRoutes = new Set(FEATURE_ENTRIES.map((candidate) => candidate.route.split('#')[0]))
    for (const item of NAV_ITEMS) {
      expect(indexedRoutes.has(item.to), `${item.label} (${item.to}) is missing from search`).toBe(true)
    }
  })

  it('indexes newly added menu items with sensible fallback metadata', () => {
    const entry = featureEntryForNavItem({
      to: '/app/release-calendar',
      label: 'Release calendar',
      icon: Search,
    })

    expect(entry.id).toBe('page-release-calendar')
    expect(entry.title).toBe('Release calendar')
    expect(entry.route).toBe('/app/release-calendar')
    expect(entry.description).toContain('Release calendar')
    expect(scoreEntry(entry, 'release')).not.toBeNull()
    expect(scoreEntry(entry, 'calendar')).not.toBeNull()
  })

  it('indexes the current menu with role and searchable labels', () => {
    expect(entry('page-log-pipelines').title).toBe('Log pipelines')
    expect(entry('page-health-checks').title).toBe('Health checks')
    expect(entry('page-maintenance').title).toBe('Maintenance windows')
    expect(entry('page-notification-deliveries').title).toBe('Notification deliveries')
    expect(entry('page-usage').title).toBe('Usage & quotas')
    expect(scoreEntry(entry('page-log-pipelines'), 'pipeline')).not.toBeNull()
  })

  it('finds Log pipelines by its title and common query variants', () => {
    const entries = accessibleEntries('VIEWER', 'USER')
    for (const query of ['log pipelines', 'pipelines', 'pipeline rules', 'log pipe']) {
      expect(
        rankEntries(entries, query).some((candidate) => candidate.id === 'page-log-pipelines'),
        `query "${query}" did not return Log pipelines`,
      ).toBe(true)
    }
  })
})
