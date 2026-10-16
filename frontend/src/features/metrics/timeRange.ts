export interface TimeRange {
  from: string
  to: string
}

export interface TimeRangePreset {
  id: string
  label: string
  hours: number
}

export const MAX_RANGE_DAYS = 30
export const MAX_RANGE_MS = MAX_RANGE_DAYS * 24 * 60 * 60 * 1000

export const TIME_RANGE_PRESETS: TimeRangePreset[] = [
  { id: '1h', label: 'Last 1h', hours: 1 },
  { id: '6h', label: 'Last 6h', hours: 6 },
  { id: '24h', label: 'Last 24h', hours: 24 },
  { id: '7d', label: 'Last 7d', hours: 24 * 7 },
]

/**
 * Resolves a preset into an explicit UTC instant pair. Values are produced with
 * `toISOString`, so the emitted range never depends on the browser timezone.
 */
export function resolvePresetRange(preset: TimeRangePreset, now: Date = new Date()): TimeRange {
  const to = now.toISOString()
  const from = new Date(now.getTime() - preset.hours * 60 * 60 * 1000).toISOString()
  return { from, to }
}

export function rangeDurationMs(from: string, to: string): number | null {
  const fromMs = Date.parse(from)
  const toMs = Date.parse(to)
  if (Number.isNaN(fromMs) || Number.isNaN(toMs)) {
    return null
  }
  return toMs - fromMs
}

export function isRangeWithinLimit(from: string, to: string): boolean {
  const duration = rangeDurationMs(from, to)
  if (duration === null || duration <= 0) {
    return false
  }
  return duration <= MAX_RANGE_MS
}
