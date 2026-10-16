import type { LucideIcon } from 'lucide-react'
import { CheckCircle2, Clock, HelpCircle, XCircle } from 'lucide-react'
import type { HealthStatus } from '../../api/healthchecks'
import { resolveHealthStatus } from '../../features/health/status'
import { Badge } from '../ui/badge'

const ICONS: Record<HealthStatus, LucideIcon> = {
  UP: CheckCircle2,
  DOWN: XCircle,
  TIMEOUT: Clock,
  UNKNOWN: HelpCircle,
}

interface HealthStatusBadgeProps {
  status: HealthStatus | null | undefined
  className?: string
}

/**
 * Renders a health status as an icon plus a text label, so the state is never conveyed by
 * color alone. A missing/unknown status is shown as UNKNOWN, never as UP.
 */
export function HealthStatusBadge({ status, className }: HealthStatusBadgeProps) {
  const resolved = resolveHealthStatus(status)
  const Icon = ICONS[resolved]

  return (
    <Badge
      variant={resolved === 'UP' ? 'success' : resolved === 'DOWN' ? 'danger' : resolved === 'TIMEOUT' ? 'warning' : 'muted'}
      className={className}
    >
      <Icon className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
      {resolved === 'UP' ? 'Up' : resolved === 'DOWN' ? 'Down' : resolved === 'TIMEOUT' ? 'Timeout' : 'Unknown'}
    </Badge>
  )
}
