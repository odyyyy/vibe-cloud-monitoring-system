import { describe, expect, it } from 'vitest'
import { BUILT_IN_METRICS, METRIC_PALETTE, metricColor, metricLabel } from './catalog'

describe('BUILT_IN_METRICS', () => {
  it('lists the host metrics collected by the agent', () => {
    expect(BUILT_IN_METRICS.map((metric) => metric.name)).toEqual([
      'system.cpu.usage',
      'system.memory.usage',
      'system.disk.usage',
      'system.load.1',
      'system.load.5',
      'system.load.15',
      'system.uptime',
      'system.network.bytes_sent',
      'system.network.bytes_recv',
      'system.network.packets_sent',
      'system.network.packets_recv',
      'system.network.drop_in',
      'system.network.drop_out',
    ])
  })

  it('uses distinct palette colors', () => {
    const colors = BUILT_IN_METRICS.map((metric) => metric.color)

    expect(new Set(colors).size).toBe(colors.length)
    for (const color of colors) {
      expect(METRIC_PALETTE).toContain(color)
    }
  })
})

describe('metricColor', () => {
  it('assigns a fixed color to built-in metrics', () => {
    expect(metricColor('system.cpu.usage')).toBe(METRIC_PALETTE[0])
    expect(metricColor('system.memory.usage')).toBe(METRIC_PALETTE[1])
    expect(metricColor('system.disk.usage')).toBe(METRIC_PALETTE[2])
  })

  it('is deterministic for custom metrics', () => {
    expect(metricColor('http.requests.total')).toBe(metricColor('http.requests.total'))
  })

  it('maps custom metrics onto the palette', () => {
    expect(METRIC_PALETTE).toContain(metricColor('http.requests.total'))
    expect(METRIC_PALETTE).toContain(metricColor('queue.depth'))
  })

  it('separates at least two different custom metrics', () => {
    const colors = ['queue.depth', 'cache.hits', 'db.connections', 'gc.pause'].map(metricColor)

    expect(new Set(colors).size).toBeGreaterThan(1)
  })
})

describe('metricLabel', () => {
  it('returns a friendly label for built-in metrics', () => {
    expect(metricLabel('system.cpu.usage')).toBe('CPU usage')
  })

  it('falls back to the raw name for custom metrics', () => {
    expect(metricLabel('my.metric')).toBe('my.metric')
  })
})
