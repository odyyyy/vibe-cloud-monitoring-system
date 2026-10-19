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

/** Compact, locale-aware count (e.g. 12,500 -> "12.5K") for meter summaries. */
export function formatUsageCount(value: number): string {
  return value.toLocaleString()
}
