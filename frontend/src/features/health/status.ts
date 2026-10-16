import type { HealthCheckResult, HealthStatus } from '../../api/healthchecks'
import { parseUtcInstant } from '../../lib/datetime'

export type HealthStatusVariant = 'success' | 'danger' | 'warning' | 'muted'

export interface HealthStatusPresentation {
  label: string
  variant: HealthStatusVariant
}

export const HEALTH_STATUS_PRESENTATION: Record<HealthStatus, HealthStatusPresentation> = {
  UP: { label: 'Up', variant: 'success' },
  DOWN: { label: 'Down', variant: 'danger' },
  TIMEOUT: { label: 'Timeout', variant: 'warning' },
  UNKNOWN: { label: 'Unknown', variant: 'muted' },
}

/** Resolves a possibly missing/unknown status to its presentation. Missing means UNKNOWN. */
export function resolveHealthStatus(status: HealthStatus | null | undefined): HealthStatus {
  return status === 'UP' || status === 'DOWN' || status === 'TIMEOUT' ? status : 'UNKNOWN'
}

export function presentationFor(status: HealthStatus | null | undefined): HealthStatusPresentation {
  return HEALTH_STATUS_PRESENTATION[resolveHealthStatus(status)]
}

export interface HealthCheckSummary {
  up: number
  down: number
  timeout: number
  unknown: number
}

interface HasLatest {
  latest: HealthCheckResult | null
}

/** Counts checks by the status of their most recent result. A check with no result is UNKNOWN. */
export function summarizeHealthChecks(entries: readonly HasLatest[]): HealthCheckSummary {
  const summary: HealthCheckSummary = { up: 0, down: 0, timeout: 0, unknown: 0 }
  for (const entry of entries) {
    switch (entry.latest?.status) {
      case 'UP':
        summary.up += 1
        break
      case 'DOWN':
        summary.down += 1
        break
      case 'TIMEOUT':
        summary.timeout += 1
        break
      default:
        summary.unknown += 1
    }
  }
  return summary
}

/**
 * Uptime percentage over the trailing window, computed only from actual results. Returns
 * {@code null} when there are no results in the window so the UI can show "No data" instead of
 * a misleading 100%.
 */
export function computeUptimePercent(
  results: readonly HealthCheckResult[],
  now: Date,
  windowHours = 24,
): number | null {
  const cutoff = now.getTime() - windowHours * 3_600_000
  const inWindow = results.filter((result) => {
    const checkedAt = parseUtcInstant(result.checkedAt)
    return checkedAt !== null && checkedAt.getTime() >= cutoff
  })
  if (inWindow.length === 0) {
    return null
  }
  const up = inWindow.filter((result) => result.status === 'UP').length
  return (up / inWindow.length) * 100
}
