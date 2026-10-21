import type { MaintenanceWindow } from '../../api/maintenanceWindows'

export type MaintenanceWindowStatus = 'ACTIVE' | 'UPCOMING' | 'ENDED' | 'DISABLED'

/**
 * Classifies a window for display. The backend stores UTC instants; `Date` compares them in
 * absolute time, so the user's local timezone does not affect the result.
 */
export function maintenanceWindowStatus(
  window: MaintenanceWindow,
  now: Date = new Date(),
): MaintenanceWindowStatus {
  if (!window.isActive) {
    return 'DISABLED'
  }
  const start = new Date(window.startTime)
  const end = new Date(window.endTime)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return 'ENDED'
  }
  if (now.getTime() < start.getTime()) {
    return 'UPCOMING'
  }
  if (now.getTime() >= end.getTime()) {
    return 'ENDED'
  }
  return 'ACTIVE'
}

export function isMaintenanceWindowActive(
  window: MaintenanceWindow,
  now: Date = new Date(),
): boolean {
  return maintenanceWindowStatus(window, now) === 'ACTIVE'
}

export function activeMaintenanceWindows(
  windows: MaintenanceWindow[],
  now: Date = new Date(),
): MaintenanceWindow[] {
  return windows.filter((window) => isMaintenanceWindowActive(window, now))
}

/** Simple display text for a window's scope; unknown names fall back to the resource id. */
export function maintenanceScopeLabel(
  window: MaintenanceWindow,
  applicationNames: Map<number, string>,
  ruleNames: Map<number, string>,
): string {
  switch (window.scopeType) {
    case 'GLOBAL':
      return 'All alerts'
    case 'APPLICATION':
      return window.scopeId === null
        ? 'Application'
        : applicationNames.get(window.scopeId) ?? `Application #${window.scopeId}`
    case 'SPECIFIC_RULE':
      return window.scopeId === null
        ? 'Alert rule'
        : ruleNames.get(window.scopeId) ?? `Alert rule #${window.scopeId}`
  }
}
