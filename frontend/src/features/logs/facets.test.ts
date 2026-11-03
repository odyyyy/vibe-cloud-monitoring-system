import { describe, expect, it } from 'vitest'
import type { LogPipelineRule } from '../../api/logPipelines'
import type { LogsQueryParams } from '../../api/logs'
import {
  MAX_FACET_FIELDS,
  buildRequestedFacetFields,
  carryOverFacetFilters,
  clearFacetFilters,
  facetFilterFor,
  facetLabel,
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

  it('maps a dynamic context field to its key and value', () => {
    const params: LogsQueryParams = {
      ...base,
      contextKey: 'order_id',
      contextValue: '1001',
    }

    expect(facetFilterFor(params, 'context.order_id')).toBe('1001')
    expect(facetFilterFor(params, 'context.user_id')).toBeUndefined()
  })
})

describe('facetLabel', () => {
  it('uses the built-in labels and the bare key for dynamic fields', () => {
    expect(facetLabel('k8s.namespace')).toBe('Namespace')
    expect(facetLabel('context.order_id')).toBe('order_id')
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

  it('toggles a dynamic context facet filter', () => {
    const next = toggleFacetFilter(base, 'context.order_id', '1001')
    expect(next.contextKey).toBe('order_id')
    expect(next.contextValue).toBe('1001')
    expect(next.page).toBe(0)
    expect(isFacetValueActive(next, 'context.order_id', '1001')).toBe(true)

    const cleared = toggleFacetFilter(next, 'context.order_id', '1001')
    expect(cleared.contextKey).toBeUndefined()
    expect(cleared.contextValue).toBeUndefined()
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
    expect(hasActiveFacetFilters({ ...base, contextKey: 'order_id', contextValue: '1001' })).toBe(true)
  })
})

describe('clearFacetFilters', () => {
  it('removes the context facet filters but keeps the level filter', () => {
    const cleared = clearFacetFilters({
      ...base,
      host: 'h',
      service: 's',
      namespace: 'n',
      contextKey: 'order_id',
      contextValue: '1001',
    })
    expect(cleared.host).toBeUndefined()
    expect(cleared.service).toBeUndefined()
    expect(cleared.namespace).toBeUndefined()
    expect(cleared.contextKey).toBeUndefined()
    expect(cleared.contextValue).toBeUndefined()
    expect(cleared.level).toBe('ERROR')
    expect(cleared.page).toBe(0)
  })
})

describe('carryOverFacetFilters', () => {
  it('copies the facet filters onto a newly applied query', () => {
    const carried = carryOverFacetFilters(
      { ...base, host: 'h1', service: 'api', namespace: 'prod', contextKey: 'order_id', contextValue: '1001' },
      { ...base, level: undefined, page: 0 },
    )
    expect(carried.host).toBe('h1')
    expect(carried.service).toBe('api')
    expect(carried.namespace).toBe('prod')
    expect(carried.contextKey).toBe('order_id')
    expect(carried.contextValue).toBe('1001')
    expect(carried.level).toBeUndefined()
  })

  it('returns the target unchanged when there is no source query', () => {
    const target: LogsQueryParams = { ...base, host: undefined }
    expect(carryOverFacetFilters(null, target)).toEqual(target)
  })
})

describe('buildRequestedFacetFields', () => {
  function rule(overrides: Partial<LogPipelineRule>): LogPipelineRule {
    return {
      id: 1,
      organizationId: 1,
      applicationId: null,
      name: 'rule',
      filter: '*',
      action: 'PARSE_REGEX',
      actionConfig: { pattern: '(?P<order_id>\\d+)' },
      orderIndex: 0,
      extractedFields: ['order_id'],
      createdAt: '2026-10-09T00:00:00Z',
      updatedAt: '2026-10-09T00:00:00Z',
      ...overrides,
    }
  }

  it('starts with the built-in fields and appends captured fields', () => {
    const fields = buildRequestedFacetFields([rule({})], 7)

    expect(fields.slice(0, 4)).toEqual(['level', 'host', 'service', 'k8s.namespace'])
    expect(fields).toContain('context.order_id')
  })

  it('ignores rules for another application and non-parsing actions', () => {
    const fields = buildRequestedFacetFields(
      [
        rule({ applicationId: 8, extractedFields: ['other_app'] }),
        rule({ applicationId: 7, extractedFields: ['mine'] }),
        rule({ applicationId: null, action: 'DROP', extractedFields: ['drop_field'] }),
      ],
      7,
    )

    expect(fields).toContain('context.mine')
    expect(fields).not.toContain('context.other_app')
    expect(fields).not.toContain('context.drop_field')
  })

  it('deduplicates fields and never exceeds the backend limit', () => {
    const fields = buildRequestedFacetFields(
      [
        rule({ extractedFields: ['a', 'a', 'b', 'c', 'd', 'e'] }),
        rule({ id: 2, extractedFields: ['b', 'f'] }),
      ],
      7,
    )

    expect(new Set(fields).size).toBe(fields.length)
    expect(fields).toHaveLength(MAX_FACET_FIELDS)
  })

  it('returns the built-in fields without rules', () => {
    expect(buildRequestedFacetFields(undefined, null)).toEqual(['level', 'host', 'service', 'k8s.namespace'])
  })
})
