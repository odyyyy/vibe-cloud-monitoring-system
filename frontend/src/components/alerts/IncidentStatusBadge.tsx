import { AlertTriangle, CheckCircle2, Eye } from 'lucide-react'
import type { IncidentStatus } from '../../api/alerts'
import { incidentStatusLabel } from '../../features/alerts/labels'
import { Badge } from '../ui/badge'

interface IncidentStatusBadgeProps {
  status: IncidentStatus
  className?: string
}

const STATUS_VARIANT = {
  FIRING: 'danger',
  ACKNOWLEDGED: 'warning',
  RESOLVED: 'muted',
} as const

const STATUS_ICON = {
  FIRING: AlertTriangle,
  ACKNOWLEDGED: Eye,
  RESOLVED: CheckCircle2,
} as const

/**
 * Renders an incident status as an icon plus a text label, so the state is never conveyed by
 * color alone. A firing badge pulses (only when the user allows motion).
 */
export function IncidentStatusBadge({ status, className }: IncidentStatusBadgeProps) {
  const Icon = STATUS_ICON[status]

  return (
    <Badge variant={STATUS_VARIANT[status]} className={className}>
      <Icon
        className={status === 'FIRING' ? 'mr-1 h-3.5 w-3.5 motion-safe:animate-pulse' : 'mr-1 h-3.5 w-3.5'}
        aria-hidden="true"
      />
      {incidentStatusLabel(status)}
    </Badge>
  )
}
