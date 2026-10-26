import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearAllRecentSearches,
  clearRecentSearches,
  MAX_RECENT_SEARCHES,
  readRecentSearches,
  rememberRecentSearch,
  SEARCH_RECENT_STORAGE_PREFIX,
  type RecentSearchItem,
} from './recentSearches'

function item(id: string, title = id): RecentSearchItem {
  return { id, type: 'feature', title, route: `/app/${id}` }
}

describe('recent searches', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('stores newest first and de-duplicates by type and id', () => {
    rememberRecentSearch(1, item('one'))
    rememberRecentSearch(1, item('two'))
    rememberRecentSearch(1, item('one', 'One again'))

    expect(readRecentSearches(1)).toEqual([
      { id: 'one', type: 'feature', title: 'One again', route: '/app/one' },
      { id: 'two', type: 'feature', title: 'two', route: '/app/two' },
    ])
  })

  it('caps the list at five entries', () => {
    for (let index = 0; index < MAX_RECENT_SEARCHES + 2; index++) {
      rememberRecentSearch(1, item(`item-${index}`))
    }
    expect(readRecentSearches(1)).toHaveLength(MAX_RECENT_SEARCHES)
    expect(readRecentSearches(1)[0]?.id).toBe('item-6')
  })

  it('isolates users', () => {
    rememberRecentSearch(1, item('one'))
    rememberRecentSearch(2, item('two'))

    expect(readRecentSearches(1).map((entry) => entry.id)).toEqual(['one'])
    expect(readRecentSearches(2).map((entry) => entry.id)).toEqual(['two'])
  })

  it('ignores malformed stored data', () => {
    window.localStorage.setItem(`${SEARCH_RECENT_STORAGE_PREFIX}1`, JSON.stringify([
      { id: 'valid', type: 'feature', title: 'Valid', route: '/app/valid' },
      { id: 'bad-type', type: 'unknown', title: 'Bad', route: '/app/bad' },
      { id: 'bad-route', type: 'feature', title: 'Bad', route: 'https://example.com' },
      { nope: true },
      'not-an-object',
    ]))

    expect(readRecentSearches(1).map((entry) => entry.id)).toEqual(['valid'])
  })

  it('returns nothing without a user or for invalid JSON', () => {
    expect(readRecentSearches(null)).toEqual([])
    window.localStorage.setItem(`${SEARCH_RECENT_STORAGE_PREFIX}1`, '{not json')
    expect(readRecentSearches(1)).toEqual([])
  })

  it('clears one user and all users on logout', () => {
    rememberRecentSearch(1, item('one'))
    rememberRecentSearch(2, item('two'))
    window.localStorage.setItem('theme', 'midnight')

    clearRecentSearches(1)
    expect(readRecentSearches(1)).toEqual([])
    expect(readRecentSearches(2)).toHaveLength(1)

    clearAllRecentSearches()
    expect(readRecentSearches(2)).toEqual([])
    expect(window.localStorage.getItem('theme')).toBe('midnight')
  })
})
