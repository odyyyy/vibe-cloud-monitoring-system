import type { ResourceSearchType } from '../../api/search'

export const SEARCH_RECENT_STORAGE_PREFIX = 'monitoring.search.recent.'
export const MAX_RECENT_SEARCHES = 5

export type RecentSearchType = 'feature' | ResourceSearchType

export interface RecentSearchItem {
  id: string
  type: RecentSearchType
  title: string
  route: string
}

const RESOURCE_TYPES: ResourceSearchType[] = [
  'applications',
  'dashboards',
  'alertRules',
  'healthChecks',
  'savedViews',
  'incidents',
]

function storageKey(userId: number): string {
  return `${SEARCH_RECENT_STORAGE_PREFIX}${userId}`
}

function isRecentSearchItem(value: unknown): value is RecentSearchItem {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const item = value as Partial<RecentSearchItem>
  return typeof item.id === 'string'
    && (item.type === 'feature' || RESOURCE_TYPES.includes(item.type as ResourceSearchType))
    && typeof item.title === 'string'
    && typeof item.route === 'string'
    && item.route.startsWith('/')
}

/** Reads at most five recent items; malformed or foreign data is discarded. */
export function readRecentSearches(userId: number | null): RecentSearchItem[] {
  if (userId === null) {
    return []
  }
  try {
    const raw = window.localStorage.getItem(storageKey(userId))
    if (!raw) {
      return []
    }
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed.filter(isRecentSearchItem).slice(0, MAX_RECENT_SEARCHES)
  } catch {
    return []
  }
}

/** Stores a recent item scoped to the user, de-duplicating by type and id. */
export function rememberRecentSearch(userId: number | null, item: RecentSearchItem): void {
  if (userId === null) {
    return
  }
  try {
    const existing = readRecentSearches(userId).filter(
      (candidate) => !(candidate.type === item.type && candidate.id === item.id),
    )
    const next = [item, ...existing].slice(0, MAX_RECENT_SEARCHES)
    window.localStorage.setItem(storageKey(userId), JSON.stringify(next))
  } catch {
    void 0
  }
}

export function clearRecentSearches(userId: number | null): void {
  if (userId === null) {
    return
  }
  try {
    window.localStorage.removeItem(storageKey(userId))
  } catch {
    void 0
  }
}

/** Removes every user's recent search history; called on logout. */
export function clearAllRecentSearches(): void {
  try {
    const keys: string[] = []
    for (let index = 0; index < window.localStorage.length; index++) {
      const key = window.localStorage.key(index)
      if (key?.startsWith(SEARCH_RECENT_STORAGE_PREFIX)) {
        keys.push(key)
      }
    }
    keys.forEach((key) => window.localStorage.removeItem(key))
  } catch {
    void 0
  }
}
