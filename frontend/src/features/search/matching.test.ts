import { Search } from 'lucide-react'
import { describe, expect, it } from 'vitest'
import { highlightSegments, rankEntries, scoreEntry } from './matching'
import type { FeatureEntry } from './registry'

function feature(overrides: Partial<FeatureEntry> & Pick<FeatureEntry, 'id' | 'title'>): FeatureEntry {
  return {
    description: '',
    group: 'Pages & Features',
    keywords: [],
    icon: Search,
    route: `/app/${overrides.id}`,
    ...overrides,
  }
}

describe('scoreEntry', () => {
  it('ranks exact, prefix, word-prefix and keyword matches above descriptions', () => {
    expect(scoreEntry(feature({ id: 'exact', title: 'Metrics' }), 'metrics')).toBe(0)
    expect(scoreEntry(feature({ id: 'prefix', title: 'Metrics explorer' }), 'met')).toBe(1)
    expect(scoreEntry(feature({ id: 'word', title: 'System metrics' }), 'metrics')).toBe(2)
    expect(scoreEntry(feature({ id: 'keyword', title: 'Charts', keywords: ['metrics'] }), 'metrics')).toBe(3)
    expect(scoreEntry(feature({ id: 'description', title: 'Charts', description: 'View metrics' }), 'metrics')).toBe(6)
  })

  it('supports fuzzy subsequence matches', () => {
    expect(scoreEntry(feature({ id: 'fuzzy', title: 'Overview' }), 'ovrview')).toBe(8)
  })

  it('is case-insensitive and rejects non-matches', () => {
    expect(scoreEntry(feature({ id: 'case', title: 'Saved Views' }), 'SAVED')).toBe(1)
    expect(scoreEntry(feature({ id: 'miss', title: 'Metrics' }), 'zzz')).toBeNull()
  })

  it('returns every entry for an empty query', () => {
    const entries = [feature({ id: 'one', title: 'One' }), feature({ id: 'two', title: 'Two' })]
    expect(rankEntries(entries, '   ')).toEqual(entries)
  })

  it('sorts by score and then title', () => {
    const entries = [
      feature({ id: 'keyword', title: 'Charts', keywords: ['metrics'] }),
      feature({ id: 'prefix', title: 'Metrics explorer' }),
      feature({ id: 'exact', title: 'Metrics' }),
      feature({ id: 'alpha', title: 'Alpha metrics' }),
    ]
    expect(rankEntries(entries, 'metrics').map((entry) => entry.id)).toEqual([
      'exact',
      'prefix',
      'alpha',
      'keyword',
    ])
  })
})

describe('highlightSegments', () => {
  it('splits around the first case-insensitive match', () => {
    expect(highlightSegments('Saved Log Views', 'log')).toEqual([
      { text: 'Saved ', match: false },
      { text: 'Log', match: true },
      { text: ' Views', match: false },
    ])
  })

  it('returns a single non-matching segment when the query is absent', () => {
    expect(highlightSegments('Saved Log Views', 'zzz')).toEqual([
      { text: 'Saved Log Views', match: false },
    ])
  })

  it('does not split for an empty query', () => {
    expect(highlightSegments('Saved Log Views', '  ')).toEqual([
      { text: 'Saved Log Views', match: false },
    ])
  })
})
