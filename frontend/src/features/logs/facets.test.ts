import { describe, expect, it } from 'vitest'
import type { LogsQueryParams } from '../../api/logs'
import {
  carryOverFacetFilters,
  clearFacetFilters,
  facetFilterFor,
  hasActiveFacetFilters,
  isFacetValueActive,
  toggleFacetFilter,
} from './facets'

const base: LogsQueryParams = {
  applicationId: 7,
  level: 'ERROR',
  from: '2024-05-01T11:00:00.000Z',
  to: '2024-05-01T12:00:00.000Z',
  page: 2,
  size: 25,
}

describe('facetFilterFor', () => {
  it('maps every facet field to its query parameter', () => {
    const params: LogsQueryParams = {
      ...base,
      host: 'server-1',
      service: 'api',
      namespace: 'prod',
    }
    expect(facetFilterFor(params, 'level')).toBe('ERROR')
    expect(facetFilterFor(params, 'host')).toBe('server-1')
    expect(facetFilterFor(params, 'service')).toBe('api')
    expect(facetFilterFor(params, 'k8s.namespace')).toBe('prod')
  })
})

describe('toggleFacetFilter', () => {
  it('adds an exact-match filter and resets the page', () => {
    const next = toggleFacetFilter(base, 'host', 'server-1')
    expect(next.host).toBe('server-1')
    expect(next.page).toBe(0)
    expect(next.level).toBe('ERROR')
  })

  it('removes the filter when the value is already active', () => {
    const active = toggleFacetFilter(base, 'host', 'server-1')
    const cleared = toggleFacetFilter(active, 'host', 'server-1')
    expect(cleared.host).toBeUndefined()
    expect(hasActiveFacetFilters(cleared)).toBe(false)
  })

  it('keeps independent filters for different fields', () => {
    const withHost = toggleFacetFilter(base, 'host', 'server-1')
    const withService = toggleFacetFilter(withHost, 'service', 'api')
    expect(withService.host).toBe('server-1')
    expect(withService.service).toBe('api')
  })

  it('keeps values with quotes and spaces unchanged', () => {
    const next = toggleFacetFilter(base, 'host', 'srv"1 & co')
    expect(next.host).toBe('srv"1 & co')
    expect(isFacetValueActive(next, 'host', 'srv"1 & co')).toBe(true)
  })

  it('toggles the level filter', () => {
    const warn = toggleFacetFilter(base, 'level', 'WARN')
    expect(warn.level).toBe('WARN')
    const cleared = toggleFacetFilter(warn, 'level', 'WARN')
    expect(cleared.level).toBeUndefined()
  })

  it('ignores an unknown level value', () => {
    const next = toggleFacetFilter(base, 'level', 'TRACE')
    expect(next.level).toBe('ERROR')
  })

  it('does not mutate the given params', () => {
    const params: LogsQueryParams = { ...base }
    toggleFacetFilter(params, 'host', 'server-1')
    expect(params.host).toBeUndefined()
  })
})

describe('hasActiveFacetFilters', () => {
  it('only reports the context facet filters, not the level', () => {
    expect(hasActiveFacetFilters(base)).toBe(false)
    expect(hasActiveFacetFilters(toggleFacetFilter(base, 'host', 'h1'))).toBe(true)
    expect(hasActiveFacetFilters({ ...base, namespace: 'prod' })).toBe(true)
  })
})

describe('clearFacetFilters', () => {
  it('removes the context facet filters but keeps the level filter', () => {
    const cleared = clearFacetFilters({ ...base, host: 'h', service: 's', namespace: 'n' })
    expect(cleared.host).toBeUndefined()
    expect(cleared.service).toBeUndefined()
    expect(cleared.namespace).toBeUndefined()
    expect(cleared.level).toBe('ERROR')
    expect(cleared.page).toBe(0)
  })
})

describe('carryOverFacetFilters', () => {
  it('copies the facet filters onto a newly applied query', () => {
    const carried = carryOverFacetFilters(
      { ...base, host: 'h1', service: 'api', namespace: 'prod' },
      { ...base, level: undefined, page: 0 },
    )
    expect(carried.host).toBe('h1')
    expect(carried.service).toBe('api')
    expect(carried.namespace).toBe('prod')
    expect(carried.level).toBeUndefined()
  })

  it('returns the target unchanged when there is no source query', () => {
    const target: LogsQueryParams = { ...base, host: undefined }
    expect(carryOverFacetFilters(null, target)).toEqual(target)
  })
})
