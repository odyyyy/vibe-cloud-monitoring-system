import type { LogLevel } from '../../api/logs'
import { Badge, type BadgeProps } from '../ui/badge'

const LEVEL_VARIANTS: Record<LogLevel, NonNullable<BadgeProps['variant']>> = {
  DEBUG: 'muted',
  INFO: 'info',
  WARN: 'warning',
  ERROR: 'danger',
}

interface LogLevelBadgeProps {
  level: LogLevel
  className?: string
}

/**
 * Severity badge. The text label is always rendered, so severity is never
 * conveyed by color alone.
 */
export function LogLevelBadge({ level, className }: LogLevelBadgeProps) {
  return (
    <Badge variant={LEVEL_VARIANTS[level]} className={className}>
      {level}
    </Badge>
  )
}
