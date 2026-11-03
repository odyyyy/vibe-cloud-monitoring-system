import { NAV_GROUPS } from './nav'

export const SIDEBAR_COLLAPSED_GROUPS_KEY = 'vibemonitor.sidebar.collapsed-groups'
export const SETTINGS_GROUP_ID = 'settings'

type StorageLike = Pick<Storage, 'getItem' | 'setItem'>

const KNOWN_GROUP_IDS = new Set<string>([
  ...NAV_GROUPS.map((group) => group.id),
  SETTINGS_GROUP_ID,
])

function defaultStorage(): StorageLike | null {
  if (typeof window === 'undefined') {
    return null
  }
  try {
    return window.localStorage
  } catch {
    return null
  }
}

/** Reads the collapsed group ids, ignoring malformed data and groups that no longer exist. */
export function readCollapsedGroups(storage: StorageLike | null = defaultStorage()): Set<string> {
  if (!storage) {
    return new Set()
  }
  try {
    const raw = storage.getItem(SIDEBAR_COLLAPSED_GROUPS_KEY)
    if (!raw) {
      return new Set()
    }
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return new Set()
    }
    return new Set(
      parsed.filter(
        (id): id is string => typeof id === 'string' && KNOWN_GROUP_IDS.has(id),
      ),
    )
  } catch {
    return new Set()
  }
}

/** Persists collapsed groups. Storage failures (quota, private mode) are non-fatal. */
export function writeCollapsedGroups(
  groups: ReadonlySet<string>,
  storage: StorageLike | null = defaultStorage(),
): void {
  if (!storage) {
    return
  }
  try {
    storage.setItem(SIDEBAR_COLLAPSED_GROUPS_KEY, JSON.stringify([...groups].sort()))
  } catch {
    // Navigation must keep working even when persistence is unavailable.
  }
}

export function toggleCollapsedGroup(
  current: ReadonlySet<string>,
  groupId: string,
): Set<string> {
  const next = new Set(current)
  if (next.has(groupId)) {
    next.delete(groupId)
  } else {
    next.add(groupId)
  }
  return next
}

export function removeCollapsedGroup(
  current: ReadonlySet<string>,
  groupId: string,
): ReadonlySet<string> {
  if (!current.has(groupId)) {
    return current
  }
  const next = new Set(current)
  next.delete(groupId)
  return next
}
