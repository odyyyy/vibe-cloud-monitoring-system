import { describe, expect, it } from 'vitest'
import {
  MAX_RANGE_MS,
  TIME_RANGE_PRESETS,
  isRangeWithinLimit,
  rangeDurationMs,
  resolvePresetRange,
} from './timeRange'

const now = new Date('2024-05-01T12:00:00.000Z')

describe('resolvePresetRange', () => {
  it('emits UTC ISO instants independent of the local timezone', () => {
    const range = resolvePresetRange(TIME_RANGE_PRESETS[0], now)
    expect(range.to).toBe('2024-05-01T12:00:00.000Z')
    expect(range.from).toBe('2024-05-01T11:00:00.000Z')
  })

  it('spans the preset duration', () => {
    const range = resolvePresetRange(TIME_RANGE_PRESETS[3], now)
    expect(rangeDurationMs(range.from, range.to)).toBe(7 * 24 * 60 * 60 * 1000)
  })
})

describe('isRangeWithinLimit', () => {
  it('accepts a range exactly at the limit', () => {
    const from = new Date(now.getTime() - MAX_RANGE_MS).toISOString()
    expect(isRangeWithinLimit(from, now.toISOString())).toBe(true)
  })

  it('rejects a range longer than the limit', () => {
    const from = new Date(now.getTime() - MAX_RANGE_MS - 1000).toISOString()
    expect(isRangeWithinLimit(from, now.toISOString())).toBe(false)
  })

  it('rejects an inverted range', () => {
    expect(isRangeWithinLimit(now.toISOString(), new Date(now.getTime() - 1000).toISOString())).toBe(false)
  })

  it('rejects unparseable values', () => {
    expect(isRangeWithinLimit('nope', now.toISOString())).toBe(false)
  })
})
