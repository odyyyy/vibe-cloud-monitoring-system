import type { LucideIcon } from 'lucide-react'
import { CheckCircle2, HelpCircle, XCircle } from 'lucide-react'
import type { AgentStatus } from '../../features/agents/status'
import { presentationFor } from '../../features/agents/status'
import { Badge } from '../ui/badge'

const ICONS: Record<AgentStatus, LucideIcon> = {
  ONLINE: CheckCircle2,
  OFFLINE: XCircle,
  UNKNOWN: HelpCircle,
}

interface AgentStatusBadgeProps {
  status: AgentStatus
  className?: string
}

/** Renders an agent status as an icon plus a text label, so state is never conveyed by color alone. */
export function AgentStatusBadge({ status, className }: AgentStatusBadgeProps) {
  const presentation = presentationFor(status)
  const Icon = ICONS[status]

  return (
    <Badge variant={presentation.variant} className={className}>
      <Icon className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
      {presentation.label}
    </Badge>
  )
}
