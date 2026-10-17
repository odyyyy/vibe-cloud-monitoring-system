import { describe, expect, it } from 'vitest'
import {
  clampDomain,
  domainsEqual,
  MIN_DOMAIN_SPAN_MS,
  panDomain,
  pixelToRatio,
  zoomDomain,
  type Domain,
} from './chartZoom'

const BOUNDS: Domain = [0, 1_000_000]

describe('clampDomain', () => {
  it('keeps a domain that is already inside the bounds', () => {
    expect(clampDomain([1000, 2000], BOUNDS)).toEqual([1000, 2000])
  })

  it('shifts a domain that starts before the bounds', () => {
    expect(clampDomain([-500, 500], BOUNDS)).toEqual([0, 1000])
  })

  it('shifts a domain that ends after the bounds', () => {
    expect(clampDomain([999_500, 1_000_500], BOUNDS)).toEqual([999_000, 1_000_000])
  })

  it('returns the full bounds when the domain is wider than available data', () => {
    expect(clampDomain([-10_000, 10_000_000], BOUNDS)).toEqual([0, 1_000_000])
  })

  it('returns the full bounds for a degenerate or empty domain', () => {
    expect(clampDomain([500, 500], BOUNDS)).toEqual(BOUNDS)
    expect(clampDomain([500, 100], BOUNDS)).toEqual(BOUNDS)
  })
})

describe('zoomDomain', () => {
  it('zooms in around the anchor point', () => {
    const result = zoomDomain(BOUNDS, BOUNDS, 0.5, 0.5)

    expect(result).toEqual([250_000, 750_000])
  })

  it('keeps the anchored value fixed when zooming in at the right edge', () => {
    const result = zoomDomain(BOUNDS, BOUNDS, 0.5, 1)

    expect(result[1]).toBe(1_000_000)
    expect(result[0]).toBe(500_000)
  })

  it('bounces back to the bounds when zooming out past the full range', () => {
    expect(zoomDomain([400_000, 600_000], BOUNDS, 10, 0.5)).toEqual(BOUNDS)
  })

  it('never produces a wrong-ordered domain', () => {
    const result = zoomDomain(BOUNDS, BOUNDS, 0.5, 0)

    expect(result[0]).toBe(0)
    expect(result[1]).toBeLessThanOrEqual(1_000_000)
    expect(result[0]).toBeLessThan(result[1])
  })

  it('does not zoom in closer than the minimum span', () => {
    const result = zoomDomain(BOUNDS, BOUNDS, 1e-6, 0.5)

    expect(result[1] - result[0]).toBeGreaterThanOrEqual(MIN_DOMAIN_SPAN_MS)
  })

  it('returns the bounds for degenerate bounds', () => {
    expect(zoomDomain([5, 5], [5, 5], 0.5)).toEqual([5, 5])
  })
})

describe('panDomain', () => {
  it('moves the window by a fraction of its width', () => {
    const result = panDomain([250_000, 500_000], BOUNDS, 0.5)

    expect(result).toEqual([375_000, 625_000])
  })

  it('clamps panning at the left edge', () => {
    expect(panDomain([0, 250_000], BOUNDS, -1)).toEqual([0, 250_000])
  })

  it('clamps panning at the right edge', () => {
    expect(panDomain([750_000, 1_000_000], BOUNDS, 1)).toEqual([750_000, 1_000_000])
  })

  it('ignores non-finite deltas', () => {
    expect(panDomain([250_000, 500_000], BOUNDS, Number.NaN)).toEqual([250_000, 500_000])
  })
})

describe('pixelToRatio', () => {
  it('maps the plot edges to 0 and 1', () => {
    expect(pixelToRatio(100, 100, 200)).toBe(0)
    expect(pixelToRatio(300, 100, 200)).toBe(1)
  })

  it('maps the middle of the plot to 0.5', () => {
    expect(pixelToRatio(200, 100, 200)).toBe(0.5)
  })

  it('clamps coordinates outside the plot area', () => {
    expect(pixelToRatio(-50, 100, 200)).toBe(0)
    expect(pixelToRatio(900, 100, 200)).toBe(1)
  })

  it('falls back to the centre for a zero-width plot', () => {
    expect(pixelToRatio(150, 100, 0)).toBe(0.5)
  })
})

describe('domainsEqual', () => {
  it('treats two nulls as equal', () => {
    expect(domainsEqual(null, null)).toBe(true)
  })

  it('treats null and a domain as different', () => {
    expect(domainsEqual(null, BOUNDS)).toBe(false)
  })

  it('compares values, not references', () => {
    expect(domainsEqual([1, 2], [1, 2])).toBe(true)
    expect(domainsEqual([1, 2], [1, 3])).toBe(false)
  })
})
