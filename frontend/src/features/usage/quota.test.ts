import { describe, expect, it } from 'vitest'
import {
  clampPercentage,
  computeUsagePercentage,
  formatUsagePercentage,
  usageLevel,
} from './quota'

describe('usageLevel', () => {
  it('is ok below the warning threshold', () => {
    expect(usageLevel(0)).toBe('ok')
    expect(usageLevel(69.9)).toBe('ok')
  })

  it('is warning from 70 up to 90', () => {
    expect(usageLevel(70)).toBe('warning')
    expect(usageLevel(89.9)).toBe('warning')
  })

  it('is critical at and above 90', () => {
    expect(usageLevel(90)).toBe('critical')
    expect(usageLevel(250)).toBe('critical')
  })

  it('treats non-finite input as ok rather than critical', () => {
    expect(usageLevel(Number.NaN)).toBe('ok')
    expect(usageLevel(Number.POSITIVE_INFINITY)).toBe('ok')
  })
})

describe('clampPercentage', () => {
  it('clamps to the 0..100 range', () => {
    expect(clampPercentage(-5)).toBe(0)
    expect(clampPercentage(42)).toBe(42)
    expect(clampPercentage(180)).toBe(100)
  })

  it('treats non-finite input as zero', () => {
    expect(clampPercentage(Number.NaN)).toBe(0)
  })
})

describe('computeUsagePercentage', () => {
  it('derives the percentage from raw counters', () => {
    expect(computeUsagePercentage(700, 1000)).toBe(70)
    expect(computeUsagePercentage(250, 500)).toBe(50)
  })

  it('keeps a small-but-real usage visible instead of collapsing it to zero', () => {
    expect(computeUsagePercentage(1, 10_000_000)).toBe(0.00001)
    expect(computeUsagePercentage(5, 10_000_000)).toBe(0.00005)
  })

  it('returns zero for empty or invalid counters', () => {
    expect(computeUsagePercentage(0, 1000)).toBe(0)
    expect(computeUsagePercentage(10, 0)).toBe(0)
    expect(computeUsagePercentage(10, -1)).toBe(0)
    expect(computeUsagePercentage(Number.NaN, 1000)).toBe(0)
  })
})

describe('formatUsagePercentage', () => {
  it('keeps two decimals below one percent so a small usage is not shown as zero', () => {
    expect(formatUsagePercentage(0.0349)).toBe('0.03%')
    expect(formatUsagePercentage(0.5)).toBe('0.5%')
  })

  it('keeps significant digits for tiny non-zero percentages', () => {
    expect(formatUsagePercentage(0.0001)).toBe('0.0001%')
    expect(formatUsagePercentage(0.00005)).toBe('0.00005%')
  })

  it('rounds to one decimal from one percent up', () => {
    expect(formatUsagePercentage(1)).toBe('1%')
    expect(formatUsagePercentage(42.37)).toBe('42.4%')
    expect(formatUsagePercentage(90)).toBe('90%')
  })

  it('prints zero for empty and invalid values', () => {
    expect(formatUsagePercentage(0)).toBe('0%')
    expect(formatUsagePercentage(-1)).toBe('0%')
    expect(formatUsagePercentage(Number.NaN)).toBe('0%')
  })
})
