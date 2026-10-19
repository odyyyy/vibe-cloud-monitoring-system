import { describe, expect, it } from 'vitest'
import { clampPercentage, usageLevel } from './quota'

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
