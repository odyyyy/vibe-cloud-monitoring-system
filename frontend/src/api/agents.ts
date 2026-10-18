import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

/**
 * An agent instance derived by the backend from the telemetry it sends.
 *
 * Only agent-provided metadata is exposed (hostname, optional version) plus the last ingest
 * instant; the online/offline state is derived on the client from `lastSeenAt`.
 */
export interface AgentNode {
  id: number
  hostname: string
  agentVersion: string | null
  lastSeenAt: string
}

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getAgents(applicationId: number): Promise<AgentNode[]> {
  return apiRequest<AgentNode[]>(`/applications/${applicationId}/agents`, { token: authToken() })
}

export const agentsQueryKey = (applicationId: number | null) => ['agents', applicationId ?? null] as const

/**
 * Loads the fleet for one application. Disabled while no application is selected, and the
 * list is refreshed on an interval (provided by the caller) so statuses decay to Offline
 * without a manual reload.
 */
export function useAgentsQuery(applicationId: number | null, refetchInterval: number | false = false) {
  return useQuery({
    queryKey: agentsQueryKey(applicationId),
    queryFn: () => getAgents(applicationId as number),
    enabled: applicationId !== null,
    refetchInterval,
  })
}
