import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import type { IncidentStatus } from '../../api/alerts'
import { incidentStatusLabel } from '../../features/alerts/labels'
import { Badge } from '../ui/badge'

interface IncidentStatusBadgeProps {
  status: IncidentStatus
  className?: string
}

/**
 * Renders an incident status as an icon plus a text label, so the state is never conveyed by
 * color alone.
 */
export function IncidentStatusBadge({ status, className }: IncidentStatusBadgeProps) {
  const isFiring = status === 'FIRING'
  const Icon = isFiring ? AlertTriangle : CheckCircle2

  return (
    <Badge variant={isFiring ? 'danger' : 'success'} className={className}>
      <Icon className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
      {incidentStatusLabel(status)}
    </Badge>
  )
}
