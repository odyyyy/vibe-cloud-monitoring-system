/**
 * Dashboard ordering is a per-user UI preference. The backend stores dashboards
 * without an explicit order, so the chosen order is persisted locally and applied
 * defensively: ids that no longer exist are dropped and new ids are appended.
 */
export const DASHBOARD_ORDER_STORAGE_PREFIX = 'vibemonitoring.dashboard-order'

export function dashboardOrderKey(userId: number | string | null | undefined): string {
  return `${DASHBOARD_ORDER_STORAGE_PREFIX}.${userId ?? 'anonymous'}`
}

/** Drops unknown ids and appends ids that are missing from the stored order. */
export function reconcileOrder(stored: readonly number[], current: readonly number[]): number[] {
  const currentSet = new Set(current)
  const result = stored.filter((id) => currentSet.has(id))
  const seen = new Set(result)
  for (const id of current) {
    if (!seen.has(id)) {
      result.push(id)
      seen.add(id)
    }
  }
  return result
}

/** Moves an item within a list from one index to another. Out-of-range moves are ignored. */
export function moveInList<T>(items: readonly T[], from: number, to: number): T[] {
  if (from < 0 || from >= items.length || to < 0 || to >= items.length || from === to) {
    return [...items]
  }
  const copy = [...items]
  const [moved] = copy.splice(from, 1)
  copy.splice(to, 0, moved)
  return copy
}

/** Moves the item with the given id to a target index. */
export function moveById(ids: readonly number[], id: number, targetIndex: number): number[] {
  return moveInList(ids, ids.indexOf(id), targetIndex)
}

export function readOrder(storage: Storage | null, key: string): number[] {
  if (!storage) {
    return []
  }
  try {
    const raw = storage.getItem(key)
    if (!raw) {
      return []
    }
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed.filter((value): value is number => typeof value === 'number' && Number.isInteger(value))
  } catch {
    return []
  }
}

export function writeOrder(storage: Storage | null, key: string, ids: readonly number[]): void {
  if (!storage) {
    return
  }
  try {
    storage.setItem(key, JSON.stringify(ids))
  } catch {
    // Ignore quota or security errors: ordering is a best-effort preference.
  }
}
