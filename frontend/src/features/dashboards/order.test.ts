import { describe, expect, it } from 'vitest'
import {
  dashboardOrderKey,
  moveById,
  moveInList,
  readOrder,
  reconcileOrder,
  writeOrder,
} from './order'

function memoryStorage(initial: Record<string, string> = {}): Storage {
  const store = new Map(Object.entries(initial))
  return {
    get length() {
      return store.size
    },
    clear: () => store.clear(),
    getItem: (key) => store.get(key) ?? null,
    key: (index) => Array.from(store.keys())[index] ?? null,
    removeItem: (key) => void store.delete(key),
    setItem: (key, value) => void store.set(key, value),
  } as Storage
}

describe('reconcileOrder', () => {
  it('preserves the stored order', () => {
    expect(reconcileOrder([3, 1, 2], [1, 2, 3])).toEqual([3, 1, 2])
  })

  it('drops unknown ids and appends missing ones', () => {
    expect(reconcileOrder([9, 2], [1, 2, 3])).toEqual([2, 1, 3])
  })
})

describe('moveInList', () => {
  it('moves an item forward', () => {
    expect(moveInList(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a'])
  })

  it('ignores out-of-range moves', () => {
    expect(moveInList(['a', 'b'], -1, 1)).toEqual(['a', 'b'])
    expect(moveInList(['a', 'b'], 0, 5)).toEqual(['a', 'b'])
  })
})

describe('moveById', () => {
  it('moves the id to the target index', () => {
    expect(moveById([1, 2, 3], 3, 0)).toEqual([3, 1, 2])
  })
})

describe('order storage', () => {
  it('round-trips a valid order', () => {
    const storage = memoryStorage()
    writeOrder(storage, 'k', [2, 1])
    expect(readOrder(storage, 'k')).toEqual([2, 1])
  })

  it('ignores non-array and non-integer payloads', () => {
    expect(readOrder(memoryStorage({ k: '"nope"' }), 'k')).toEqual([])
    expect(readOrder(memoryStorage({ k: '{"a":1}' }), 'k')).toEqual([])
    expect(readOrder(memoryStorage({ k: '[1,"2",3.5]' }), 'k')).toEqual([1])
  })

  it('returns an empty order without storage', () => {
    expect(readOrder(null, 'k')).toEqual([])
    expect(() => writeOrder(null, 'k', [1])).not.toThrow()
  })

  it('scopes the key by user id', () => {
    expect(dashboardOrderKey(7)).toContain('7')
    expect(dashboardOrderKey(null)).toContain('anonymous')
  })
})
