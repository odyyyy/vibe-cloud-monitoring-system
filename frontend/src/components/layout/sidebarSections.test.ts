import { describe, expect, it } from 'vitest'
import {
  SIDEBAR_COLLAPSED_GROUPS_KEY,
  SETTINGS_GROUP_ID,
  readCollapsedGroups,
  removeCollapsedGroup,
  toggleCollapsedGroup,
  writeCollapsedGroups,
} from './sidebarSections'

function memoryStorage(initial: Record<string, string> = {}) {
  const data = new Map(Object.entries(initial))
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => {
      data.set(key, value)
    },
    value: (key: string) => data.get(key) ?? null,
  }
}

describe('sidebar section state', () => {
  it('returns an empty set when nothing is stored', () => {
    expect(readCollapsedGroups(memoryStorage())).toEqual(new Set())
  })

  it('round-trips known collapsed groups', () => {
    const storage = memoryStorage()
    writeCollapsedGroups(new Set(['observability', SETTINGS_GROUP_ID]), storage)

    expect(storage.value(SIDEBAR_COLLAPSED_GROUPS_KEY)).toBe(
      JSON.stringify(['observability', SETTINGS_GROUP_ID].sort()),
    )
    expect(readCollapsedGroups(storage)).toEqual(new Set(['observability', SETTINGS_GROUP_ID]))
  })

  it('ignores malformed and unknown group ids', () => {
    expect(readCollapsedGroups(memoryStorage({ [SIDEBAR_COLLAPSED_GROUPS_KEY]: 'not-json' }))).toEqual(new Set())
    expect(readCollapsedGroups(memoryStorage({ [SIDEBAR_COLLAPSED_GROUPS_KEY]: '{"a":1}' }))).toEqual(new Set())
    expect(
      readCollapsedGroups(memoryStorage({
        [SIDEBAR_COLLAPSED_GROUPS_KEY]: JSON.stringify(['observability', 'removed-group', 42]),
      })),
    ).toEqual(new Set(['observability']))
  })

  it('toggles a group without mutating the previous set', () => {
    const initial = new Set(['alerting'])
    const collapsed = toggleCollapsedGroup(initial, 'observability')
    expect(collapsed).toEqual(new Set(['alerting', 'observability']))
    expect(initial).toEqual(new Set(['alerting']))

    const expanded = toggleCollapsedGroup(collapsed, 'alerting')
    expect(expanded).toEqual(new Set(['observability']))
  })

  it('removes a collapsed group while keeping others', () => {
    expect(removeCollapsedGroup(new Set(['alerting', 'observability']), 'alerting')).toEqual(
      new Set(['observability']),
    )
  })
})
