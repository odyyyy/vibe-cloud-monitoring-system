import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export interface WorkspaceSummary {
  applicationCount: number
  metricNameCount24h: number
  logEventCount24h: number
}

export const workspaceSummaryQueryKey = ['overview', 'summary'] as const

export function getWorkspaceSummary(): Promise<WorkspaceSummary> {
  return apiRequest<WorkspaceSummary>('/v1/overview', { token: useAuthStore.getState().token })
}

export function useWorkspaceSummaryQuery() {
  return useQuery({
    queryKey: workspaceSummaryQueryKey,
    queryFn: getWorkspaceSummary,
  })
}
