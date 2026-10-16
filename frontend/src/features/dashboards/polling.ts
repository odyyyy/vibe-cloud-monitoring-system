export interface PollingOption {
  value: number
  label: string
}

export const MIN_POLLING_INTERVAL_MS = 10000
export const DEFAULT_POLLING_INTERVAL_MS = 30000

export const POLLING_OPTIONS: PollingOption[] = [
  { value: 10000, label: '10s' },
  { value: 30000, label: '30s' },
  { value: 60000, label: '1m' },
  { value: 300000, label: '5m' },
]

/** Clamps an interval so it can never poll faster than the minimum. */
export function sanitizePollingInterval(value: number): number {
  if (!Number.isFinite(value) || value < MIN_POLLING_INTERVAL_MS) {
    return DEFAULT_POLLING_INTERVAL_MS
  }
  return Math.floor(value)
}
