import { parseUtcInstant } from '../../lib/datetime'

export type AgentStatus = 'ONLINE' | 'OFFLINE' | 'UNKNOWN'

/**
 * An agent is considered online while its last ingest is newer than this window; otherwise it
 * is offline. The threshold is a display concern only, so it lives with the fleet UI.
 */
export const AGENT_ONLINE_THRESHOLD_MS = 5 * 60 * 1000

export type AgentStatusVariant = 'success' | 'muted'

export interface AgentStatusPresentation {
  label: string
  variant: AgentStatusVariant
}

export const AGENT_STATUS_PRESENTATION: Record<AgentStatus, AgentStatusPresentation> = {
  ONLINE: { label: 'Online', variant: 'success' },
  OFFLINE: { label: 'Offline', variant: 'muted' },
  UNKNOWN: { label: 'Unknown', variant: 'muted' },
}

/**
 * Derives the fleet status from the last ingest instant.
 *
 * A missing or unparseable timestamp resolves to UNKNOWN rather than ONLINE, so an agent whose
 * data cannot be interpreted is never presented as healthy. A future timestamp (clock skew) is
 * treated as fresh.
 */
export function resolveAgentStatus(lastSeenAt: string | null | undefined, now: Date): AgentStatus {
  if (!lastSeenAt) {
    return 'UNKNOWN'
  }
  const seen = parseUtcInstant(lastSeenAt)
  if (!seen) {
    return 'UNKNOWN'
  }
  return now.getTime() - seen.getTime() < AGENT_ONLINE_THRESHOLD_MS ? 'ONLINE' : 'OFFLINE'
}

export function presentationFor(status: AgentStatus): AgentStatusPresentation {
  return AGENT_STATUS_PRESENTATION[status]
}
