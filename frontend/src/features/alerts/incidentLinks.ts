import type { AlertIncident } from '../../api/alerts'

/** Deep link to the window and filters that opened a log incident. */
export function logExplorerHref(incident: AlertIncident): string | null {
  if (
    incident.ruleType !== 'LOG' ||
    incident.applicationId === null ||
    incident.windowMinutes === null
  ) {
    return null
  }
  const to = new Date(incident.triggeredAt)
  if (Number.isNaN(to.getTime())) {
    return null
  }
  const from = new Date(to.getTime() - incident.windowMinutes * 60_000)
  const params = new URLSearchParams({
    applicationId: String(incident.applicationId),
    from: from.toISOString(),
    to: to.toISOString(),
  })
  if (incident.logLevel) {
    params.set('level', incident.logLevel)
  }
  if (incident.logQuery) {
    params.set('searchText', incident.logQuery)
  }
  return `/app/logs?${params.toString()}`
}
