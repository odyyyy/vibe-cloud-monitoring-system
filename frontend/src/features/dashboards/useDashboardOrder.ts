import { useCallback, useEffect, useMemo, useState } from 'react'
import { dashboardOrderKey, moveById, moveInList, readOrder, reconcileOrder, writeOrder } from './order'

function getStorage(): Storage | null {
  try {
    return typeof window !== 'undefined' ? window.localStorage : null
  } catch {
    return null
  }
}

export interface DashboardOrder {
  orderedIds: number[]
  moveBy: (id: number, delta: number) => void
  moveToIndex: (id: number, index: number) => void
}

/**
 * Applies a persisted, per-user dashboard order. The stored order is reconciled
 * with the ids currently returned by the backend on every render, so renames,
 * additions and deletions never corrupt it.
 */
export function useDashboardOrder(userId: number | null, dashboardIds: number[]): DashboardOrder {
  const storage = useMemo(getStorage, [])
  const key = dashboardOrderKey(userId)
  const [storedOrder, setStoredOrder] = useState<number[]>([])

  useEffect(() => {
    setStoredOrder(readOrder(storage, key))
  }, [storage, key])

  const orderedIds = useMemo(
    () => reconcileOrder(storedOrder, dashboardIds),
    [storedOrder, dashboardIds],
  )

  const commit = useCallback(
    (ids: number[]) => {
      setStoredOrder(ids)
      writeOrder(storage, key, ids)
    },
    [storage, key],
  )

  const moveBy = useCallback(
    (id: number, delta: number) => {
      const ids = reconcileOrder(storedOrder, dashboardIds)
      const from = ids.indexOf(id)
      commit(moveInList(ids, from, from + delta))
    },
    [storedOrder, dashboardIds, commit],
  )

  const moveToIndex = useCallback(
    (id: number, index: number) => {
      const ids = reconcileOrder(storedOrder, dashboardIds)
      commit(moveById(ids, id, index))
    },
    [storedOrder, dashboardIds, commit],
  )

  return { orderedIds, moveBy, moveToIndex }
}
