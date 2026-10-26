import { describe, expect, it } from 'vitest'
import {
  accessibleEntries,
  canAccessEntry,
  FEATURE_ENTRIES,
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
