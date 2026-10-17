/**
 * Pure helpers for the interactive time axis of {@link MetricChart}.
 *
 * A domain is a `[start, end]` pair of millisecond timestamps. `bounds` is the full
 * extent of the loaded data; the visible domain is always kept inside it so the chart
 * can never scroll past the available points.
 */

export type Domain = readonly [number, number]

/** Never zoom in closer than one minute, even for very wide data ranges. */
export const MIN_DOMAIN_SPAN_MS = 60_000

/** Smallest allowed window as a fraction of the full range. */
const MIN_SPAN_RATIO = 1 / 200

export function domainsEqual(a: Domain | null, b: Domain | null): boolean {
  if (a === null || b === null) {
    return a === b
  }
  return a[0] === b[0] && a[1] === b[1]
}

/**
 * Shifts a domain so it lies fully inside `bounds`. If the domain is wider than the
 * bounds, or degenerate, the full bounds are returned.
 */
export function clampDomain(domain: Domain, bounds: Domain): Domain {
  const [lo, hi] = bounds
  const fullSpan = hi - lo
  const width = domain[1] - domain[0]

  if (fullSpan <= 0 || width <= 0 || width >= fullSpan) {
    return [lo, hi]
  }

  let start = domain[0]
  let end = domain[1]

  if (start < lo) {
    end += lo - start
    start = lo
  }
  if (end > hi) {
    start -= end - hi
    end = hi
  }

  return [start, end]
}

/**
 * Zooms `current` by `factor` (< 1 zooms in, > 1 zooms out) keeping the point at
 * `anchorRatio` (0 = left edge, 1 = right edge) visually fixed.
 */
export function zoomDomain(current: Domain, bounds: Domain, factor: number, anchorRatio = 0.5): Domain {
  const [lo, hi] = bounds
  const fullSpan = hi - lo

  if (fullSpan <= 0 || !Number.isFinite(factor) || factor <= 0) {
    return [lo, hi]
  }

  const ratio = Math.min(Math.max(anchorRatio, 0), 1)
  const currentSpan = Math.min(Math.max(current[1] - current[0], 0), fullSpan)
  const minSpan = Math.max(MIN_DOMAIN_SPAN_MS, fullSpan * MIN_SPAN_RATIO)
  const nextSpan = Math.min(Math.max(currentSpan * factor, minSpan), fullSpan)

  const anchor = current[0] + currentSpan * ratio
  const start = anchor - nextSpan * ratio

  return clampDomain([start, start + nextSpan], bounds)
}

/** Pans `current` horizontally by a fraction of its own width. */
export function panDomain(current: Domain, bounds: Domain, deltaRatio: number): Domain {
  const span = current[1] - current[0]
  if (!Number.isFinite(deltaRatio) || span <= 0) {
    return clampDomain(current, bounds)
  }

  const delta = deltaRatio * span
  return clampDomain([current[0] + delta, current[1] + delta], bounds)
}

/** Maps an absolute x coordinate to a 0..1 ratio across the plot area. */
export function pixelToRatio(clientX: number, plotLeft: number, plotWidth: number): number {
  if (!Number.isFinite(clientX) || plotWidth <= 0) {
    return 0.5
  }
  return Math.min(Math.max((clientX - plotLeft) / plotWidth, 0), 1)
}
