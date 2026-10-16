import { describe, expect, it } from 'vitest'
import { parseWidgetConfig, serializeWidgetConfig } from './schemas'
import { DEFAULT_POLLING_INTERVAL_MS, MIN_POLLING_INTERVAL_MS, sanitizePollingInterval } from './polling'

describe('parseWidgetConfig', () => {
  it('parses a valid metric chart config', () => {
    const result = parseWidgetConfig(
      'METRIC_CHART',
      JSON.stringify({
        applicationId: 3,
        metricName: 'cpu_usage',
        aggregation: 'AVG',
        intervalMinutes: 5,
        timeRangeHours: 1,
      }),
    )
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.type).toBe('METRIC_CHART')
    }
  })

  it('rejects malformed JSON', () => {
    const result = parseWidgetConfig('METRIC_CHART', '{not json')
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error).toContain('JSON')
    }
  })

  it('rejects a metric chart without a metric name', () => {
    const result = parseWidgetConfig(
      'METRIC_CHART',
      JSON.stringify({
        applicationId: 3,
        metricName: '',
        aggregation: 'AVG',
        intervalMinutes: 5,
        timeRangeHours: 1,
      }),
    )
    expect(result.success).toBe(false)
  })

  it('rejects a log table with an unknown level', () => {
    const result = parseWidgetConfig(
      'LOG_TABLE',
      JSON.stringify({ applicationId: 1, level: 'TRACE', timeRangeHours: 1 }),
    )
    expect(result.success).toBe(false)
  })

  it('parses a status config', () => {
    const result = parseWidgetConfig('STATUS', JSON.stringify({ applicationId: 9, timeRangeHours: 6 }))
    expect(result.success).toBe(true)
  })

  it('round-trips a serialized config', () => {
    const serialized = serializeWidgetConfig({
      applicationId: 2,
      metricName: 'mem_used',
      aggregation: 'MAX',
      intervalMinutes: 15,
      timeRangeHours: 24,
    })
    const result = parseWidgetConfig('METRIC_CHART', serialized)
    expect(result.success).toBe(true)
  })
})

describe('sanitizePollingInterval', () => {
  it('clamps intervals below the minimum to the default', () => {
    expect(sanitizePollingInterval(1000)).toBe(DEFAULT_POLLING_INTERVAL_MS)
    expect(sanitizePollingInterval(MIN_POLLING_INTERVAL_MS - 1)).toBe(DEFAULT_POLLING_INTERVAL_MS)
  })

  it('keeps valid intervals', () => {
    expect(sanitizePollingInterval(60000)).toBe(60000)
  })

  it('falls back for non-finite input', () => {
    expect(sanitizePollingInterval(Number.NaN)).toBe(DEFAULT_POLLING_INTERVAL_MS)
  })
})
