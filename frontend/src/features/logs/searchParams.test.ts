import { describe, expect, it } from 'vitest'
import {
  DEFAULT_LOG_FILTERS,
  filtersFromSearchParams,
  filtersToSearchParams,
  parseLogSearchParams,
} from './searchParams'

const validParams = {
  applicationId: '7',
  level: 'ERROR',
  from: '2024-05-01T11:00:00.000Z',
  to: '2024-05-01T12:00:00.000Z',
  searchText: 'timeout',
  page: '2',
  size: '50',
}

function buildParams(overrides: Record<string, string> = {}): URLSearchParams {
  return new URLSearchParams({ ...validParams, ...overrides })
}

describe('parseLogSearchParams', () => {
  it('returns no query when required filters are missing', () => {
    const parsed = parseLogSearchParams(new URLSearchParams())
    expect(parsed.params).toBeNull()
    expect(parsed.error).toBeNull()
  })

  it('parses a valid query', () => {
    const parsed = parseLogSearchParams(buildParams())
    expect(parsed.error).toBeNull()
    expect(parsed.params).toEqual({
      applicationId: 7,
      level: 'ERROR',
      from: validParams.from,
      to: validParams.to,
      searchText: 'timeout',
      page: 2,
      size: 50,
    })
  })

  it('parses the facet filters', () => {
    const parsed = parseLogSearchParams(
      buildParams({ host: 'srv "1"', service: 'api', namespace: 'prod' }),
    )
    expect(parsed.error).toBeNull()
    expect(parsed.params?.host).toBe('srv "1"')
    expect(parsed.params?.service).toBe('api')
    expect(parsed.params?.namespace).toBe('prod')
  })

  it('rejects a facet filter value that is too long', () => {
    const parsed = parseLogSearchParams(buildParams({ host: 'a'.repeat(257) }))
    expect(parsed.params).toBeNull()
    expect(parsed.error).toContain('256')
  })

  it('applies pagination defaults when omitted', () => {
    const parsed = parseLogSearchParams(
      buildParams({ page: '', size: '', level: '', searchText: '' }),
    )
    expect(parsed.params).toEqual({
      applicationId: 7,
      from: validParams.from,
      to: validParams.to,
      page: 0,
      size: 25,
    })
  })

  it('rejects inverted ranges', () => {
    const parsed = parseLogSearchParams(buildParams({ from: validParams.to, to: validParams.from }))
    expect(parsed.params).toBeNull()
    expect(parsed.error).toBeTruthy()
  })

  it('rejects page sizes above 100', () => {
    const parsed = parseLogSearchParams(buildParams({ size: '500' }))
    expect(parsed.params).toBeNull()
    expect(parsed.error).toContain('100')
  })

  it('rejects ranges longer than 30 days', () => {
    const parsed = parseLogSearchParams(
      buildParams({ from: '2024-01-01T00:00:00.000Z', to: '2024-05-01T00:00:00.000Z' }),
    )
    expect(parsed.params).toBeNull()
    expect(parsed.error).toContain('30 days')
  })
})

describe('filtersFromSearchParams', () => {
  it('falls back to defaults when the URL is empty', () => {
    const filters = filtersFromSearchParams(new URLSearchParams(), DEFAULT_LOG_FILTERS)
    expect(filters).toEqual(DEFAULT_LOG_FILTERS)
  })

  it('keeps a partial application selection for the draft', () => {
    const filters = filtersFromSearchParams(
      new URLSearchParams({ applicationId: '3', level: 'WARN' }),
      DEFAULT_LOG_FILTERS,
    )
    expect(filters.applicationId).toBe(3)
    expect(filters.level).toBe('WARN')
  })
})

describe('filtersToSearchParams', () => {
  it('omits default level, blank text and default pagination', () => {
    const serialized = filtersToSearchParams({
      applicationId: 7,
      from: validParams.from,
      to: validParams.to,
      page: 0,
      size: 25,
    })
    expect(serialized).toEqual({
      applicationId: '7',
      from: validParams.from,
      to: validParams.to,
    })
  })

  it('serializes optional filters and non-default pagination', () => {
    const serialized = filtersToSearchParams({
      applicationId: 7,
      level: 'DEBUG',
      from: validParams.from,
      to: validParams.to,
      searchText: 'boot',
      page: 3,
      size: 100,
    })
    expect(serialized).toEqual({
      applicationId: '7',
      level: 'DEBUG',
      from: validParams.from,
      to: validParams.to,
      searchText: 'boot',
      page: '3',
      size: '100',
    })
  })

  it('round-trips the facet filters through the URL', () => {
    const committed = {
      applicationId: 7,
      level: 'ERROR' as const,
      from: validParams.from,
      to: validParams.to,
      host: 'srv "1" & co',
      service: 'api',
      namespace: 'prod',
      page: 0,
      size: 25,
    }

    const serialized = filtersToSearchParams(committed)
    expect(serialized.host).toBe('srv "1" & co')
    expect(serialized.service).toBe('api')
    expect(serialized.namespace).toBe('prod')

    const parsed = parseLogSearchParams(new URLSearchParams(serialized))
    expect(parsed.error).toBeNull()
    expect(parsed.params).toEqual(committed)
  })
})
