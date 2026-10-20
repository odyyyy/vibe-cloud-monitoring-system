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

  it('parses a gauge config and rejects a missing metric name', () => {
    const valid = parseWidgetConfig(
      'GAUGE',
      JSON.stringify({
        applicationId: 4,
        metricName: 'jvm_memory_used_bytes',
        label: 'JVM Heap Usage',
        unit: 'bytes',
        timeRangeHours: 1,
      }),
    )
    expect(valid.success).toBe(true)
    if (valid.success && valid.data.type === 'GAUGE') {
      expect(valid.data.config.label).toBe('JVM Heap Usage')
    }

    const invalid = parseWidgetConfig(
      'GAUGE',
      JSON.stringify({ applicationId: 4, metricName: '', timeRangeHours: 1 }),
    )
    expect(invalid.success).toBe(false)
  })

  it('keeps an optional group-by tag on a metric chart', () => {
    const serialized = serializeWidgetConfig({
      applicationId: 2,
      metricName: 'ingest_requests_total',
      aggregation: 'SUM',
      intervalMinutes: 1,
      groupByTag: 'status',
      timeRangeHours: 1,
    })
    const result = parseWidgetConfig('METRIC_CHART', serialized)

    expect(result.success).toBe(true)
    if (result.success && result.data.type === 'METRIC_CHART') {
      expect(result.data.config.groupByTag).toBe('status')
    }
  })

  it('rejects an unsafe group-by tag', () => {
    const result = parseWidgetConfig(
      'METRIC_CHART',
      JSON.stringify({
        applicationId: 2,
        metricName: 'requests',
        aggregation: 'SUM',
        intervalMinutes: 1,
        groupByTag: 'status; DROP TABLE metric_samples',
        timeRangeHours: 1,
      }),
    )
    expect(result.success).toBe(false)
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
