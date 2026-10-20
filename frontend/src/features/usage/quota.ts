export type UsageLevel = 'ok' | 'warning' | 'critical'

/** Warning band starts at 70%, critical at 90% of the plan limit. */
export const USAGE_WARNING_THRESHOLD = 70
export const USAGE_CRITICAL_THRESHOLD = 90

/**
 * Maps a usage percentage to a severity band. The bands are deliberately explicit (rather
 * than a continuous gradient) so the meaning is stable and testable:
 * below 70% is healthy, 70-90% is a warning, 90% and above is critical.
 */
export function usageLevel(percentage: number): UsageLevel {
  if (!Number.isFinite(percentage)) {
    return 'ok'
  }
  if (percentage >= USAGE_CRITICAL_THRESHOLD) {
    return 'critical'
  }
  if (percentage >= USAGE_WARNING_THRESHOLD) {
    return 'warning'
  }
  return 'ok'
}

/** Clamps a percentage to a 0..100 range for rendering a progress bar width. */
export function clampPercentage(percentage: number): number {
  if (!Number.isFinite(percentage) || percentage < 0) {
    return 0
  }
  return Math.min(percentage, 100)
}

/** Locale-aware count for meter summaries (e.g. 12500 -> "12,500"). */
export function formatUsageCount(value: number): string {
  return value.toLocaleString()
}

/**
 * Computes the consumed share of a quota from the raw counters.
 *
 * <p>The UI must not rely solely on a server-provided percentage: an older or rounding
 * backend can report {@code 0.0} for a small-but-real usage, and the page would then look
 * like nothing was counted. Returns {@code 0} when the limit is missing or non-positive.
 */
export function computeUsagePercentage(used: number, limit: number): number {
  if (!Number.isFinite(used) || !Number.isFinite(limit) || limit <= 0 || used <= 0) {
    return 0
  }
  return (used * 100) / limit
}

/**
 * Formats a usage percentage for display.
 *
 * <p>Plan limits are in the millions of items, so a small but real usage would be printed as
 * "0%" and look like nothing was counted. Below one percent the value keeps two decimals;
 * below 0.01% it keeps two significant digits (e.g. "0.0001%") so tiny usage stays visible.
 */
export function formatUsagePercentage(percentage: number): string {
  if (!Number.isFinite(percentage) || percentage <= 0) {
    return '0%'
  }
  if (percentage < 0.01) {
    return `${Number(percentage.toPrecision(2))}%`
  }
  if (percentage < 1) {
    return `${Math.round(percentage * 100) / 100}%`
  }
  return `${Math.round(percentage * 10) / 10}%`
}
