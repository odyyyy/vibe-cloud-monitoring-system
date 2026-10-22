import type { StatusPageComponentStatus } from '../../api/statusPages'

export type StatusPageBadgeVariant = 'success' | 'danger' | 'warning' | 'muted'

export interface StatusPagePresentation {
  label: string
  variant: StatusPageBadgeVariant
}

export const OVERALL_STATUS_PRESENTATION: Record<StatusPageComponentStatus, StatusPagePresentation> = {
  UP: { label: 'All Systems Operational', variant: 'success' },
  DEGRADED: { label: 'Partial Outage', variant: 'warning' },
  DOWN: { label: 'Major Outage', variant: 'danger' },
  UNKNOWN: { label: 'Status Unknown', variant: 'muted' },
}

export const COMPONENT_STATUS_PRESENTATION: Record<StatusPageComponentStatus, StatusPagePresentation> = {
  UP: { label: 'Operational', variant: 'success' },
  DEGRADED: { label: 'Degraded Performance', variant: 'warning' },
  DOWN: { label: 'Major Outage', variant: 'danger' },
  UNKNOWN: { label: 'Unknown', variant: 'muted' },
}

export function overallStatusPresentation(status: StatusPageComponentStatus): StatusPagePresentation {
  return OVERALL_STATUS_PRESENTATION[status]
}

export function componentStatusPresentation(status: StatusPageComponentStatus): StatusPagePresentation {
  return COMPONENT_STATUS_PRESENTATION[status]
}
