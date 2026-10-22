import { describe, expect, it } from 'vitest'
import type { SavedLogView } from '../../api/savedLogViews'
import {
  savedViewFormSchema,
  savedViewMatchesParams,
  savedViewQueryError,
  savedViewToSearchParams,
  summarizeSavedViewQuery,
  toSavedViewQuery,
} from './savedViews'

const params = {
  applicationId: 7,
  level: 'ERROR' as const,
  from: '2024-05-01T11:00:00.000Z',
  to: '2024-05-01T12:00:00.000Z',
  searchText: 'timeout',
  page: 3,
  size: 100,
}

describe('toSavedViewQuery', () => {
  it('captures filters and drops pagination', () => {
    expect(toSavedViewQuery(params)).toEqual({
      applicationId: 7,
      level: 'ERROR',
      searchText: 'timeout',
      timeRange: { from: params.from, to: params.to },
    })
  })

  it('leaves optional filters undefined when absent', () => {
    const query = toSavedViewQuery({ ...params, level: undefined, searchText: undefined })
    expect(query.level).toBeUndefined()
    expect(query.searchText).toBeUndefined()
  })
})

describe('savedViewToSearchParams', () => {
  const view: SavedLogView = {
    id: 1,
    name: 'Errors',
    queryParams: {
      applicationId: 7,
      level: 'ERROR',
      searchText: 'timeout',
      timeRange: { from: params.from, to: params.to },
    },
    createdAt: '2024-05-01T12:00:00.000Z',
    updatedAt: '2024-05-01T12:00:00.000Z',
  }

  it('serializes to URL params without pagination noise', () => {
    expect(savedViewToSearchParams(view)).toEqual({
      applicationId: '7',
      level: 'ERROR',
      from: params.from,
      to: params.to,
      searchText: 'timeout',
    })
  })

  it('omits optional filters for a name-only view', () => {
    const bare: SavedLogView = {
      ...view,
      queryParams: {
        applicationId: 3,
        timeRange: { from: params.from, to: params.to },
      },
    }
    expect(savedViewToSearchParams(bare)).toEqual({
      applicationId: '3',
      from: params.from,
      to: params.to,
    })
  })
})

describe('savedViewQueryError', () => {
  it('flags angle brackets in the search text', () => {
    const error = savedViewQueryError({
      applicationId: 1,
      searchText: '<script>',
      timeRange: { from: params.from, to: params.to },
    })
    expect(error).toContain('<')
  })

  it('accepts a clean query', () => {
    expect(
      savedViewQueryError({
        applicationId: 1,
        searchText: 'ok',
        timeRange: { from: params.from, to: params.to },
      }),
    ).toBeNull()
  })
})

describe('summarizeSavedViewQuery', () => {
  it('uses the application name when available', () => {
    expect(
      summarizeSavedViewQuery({ applicationId: 7, level: 'WARN', timeRange: { from: '', to: '' } }, 'api'),
    ).toBe('api · WARN')
  })
})

describe('savedViewMatchesParams', () => {
  const view: SavedLogView = {
    id: 1,
    name: 'Errors',
    queryParams: {
      applicationId: 7,
      level: 'ERROR',
      searchText: 'timeout',
      timeRange: { from: params.from, to: params.to },
    },
    createdAt: '2024-05-01T12:00:00.000Z',
    updatedAt: '2024-05-01T12:00:00.000Z',
  }

  it('matches an identical committed query regardless of pagination defaults', () => {
    expect(
      savedViewMatchesParams(view, {
        applicationId: 7,
        level: 'ERROR',
        from: params.from,
        to: params.to,
        searchText: 'timeout',
        page: 0,
        size: 25,
      }),
    ).toBe(true)
  })

  it('does not match when a filter differs', () => {
    expect(
      savedViewMatchesParams(view, {
        applicationId: 7,
        level: 'WARN',
        from: params.from,
        to: params.to,
        searchText: 'timeout',
        page: 0,
        size: 25,
      }),
    ).toBe(false)
  })
})

describe('savedViewFormSchema', () => {
  it('trims and rejects an empty name', () => {
    expect(savedViewFormSchema.safeParse({ name: '  ' }).success).toBe(false)
    const parsed = savedViewFormSchema.safeParse({ name: '  Errors  ' })
    expect(parsed.success).toBe(true)
    if (parsed.success) {
      expect(parsed.data.name).toBe('Errors')
    }
  })
})
