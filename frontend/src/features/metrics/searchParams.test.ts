import { describe, expect, it } from 'vitest'
import {
  DEFAULT_METRIC_FILTERS,
  filtersFromSearchParams,
  filtersToSearchParams,
  parseMetricSearchParams,
} from './searchParams'

const validParams = {
  applicationId: '7',
  metricName: 'cpu_usage',
  from: '2024-05-01T11:00:00.000Z',
  to: '2024-05-01T12:00:00.000Z',
  aggregation: 'AVG',
  intervalMinutes: '5',
}

function buildParams(overrides: Record<string, string> = {}): URLSearchParams {
  return new URLSearchParams({ ...validParams, ...overrides })
}

describe('parseMetricSearchParams', () => {
  it('returns no query when required filters are missing', () => {
    const parsed = parseMetricSearchParams(new URLSearchParams())
    expect(parsed.params).toBeNull()
    expect(parsed.error).toBeNull()
  })

  it('parses a valid query', () => {
    const parsed = parseMetricSearchParams(buildParams())
    expect(parsed.error).toBeNull()
    expect(parsed.params).toEqual({
      applicationId: 7,
      metricName: 'cpu_usage',
      from: validParams.from,
      to: validParams.to,
      aggregation: 'AVG',
      intervalMinutes: 5,
    })
  })

  it('rejects inverted ranges', () => {
    const parsed = parseMetricSearchParams(
      buildParams({ from: validParams.to, to: validParams.from }),
    )
    expect(parsed.params).toBeNull()
    expect(parsed.error).toBeTruthy()
  })

  it('rejects ranges longer than 30 days', () => {
    const parsed = parseMetricSearchParams(
      buildParams({ from: '2024-01-01T00:00:00.000Z', to: '2024-05-01T00:00:00.000Z' }),
    )
    expect(parsed.params).toBeNull()
    expect(parsed.error).toContain('30 days')
  })
})

describe('filtersFromSearchParams', () => {
  it('falls back to defaults when the URL is empty', () => {
    const filters = filtersFromSearchParams(new URLSearchParams(), DEFAULT_METRIC_FILTERS)
    expect(filters).toEqual(DEFAULT_METRIC_FILTERS)
  })

  it('keeps a partial application selection for the draft', () => {
    const filters = filtersFromSearchParams(
      new URLSearchParams({ applicationId: '3', metricName: 'mem' }),
      DEFAULT_METRIC_FILTERS,
    )
    expect(filters.applicationId).toBe(3)
    expect(filters.metricName).toBe('mem')
  })
})

describe('filtersToSearchParams', () => {
  it('serializes every field as a string', () => {
    const serialized = filtersToSearchParams({
      applicationId: 7,
      metricName: 'cpu_usage',
      from: validParams.from,
      to: validParams.to,
      aggregation: 'MAX',
      intervalMinutes: 15,
    })
    expect(serialized).toEqual({
      applicationId: '7',
      metricName: 'cpu_usage',
      from: validParams.from,
      to: validParams.to,
      aggregation: 'MAX',
      intervalMinutes: '15',
    })
  })
})
