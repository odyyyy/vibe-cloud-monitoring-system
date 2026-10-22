import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export type RetentionDataType = 'LOGS' | 'METRICS' | 'TRACES'
export type PurgeRunStatus = 'SUCCESS' | 'FAILED'

export interface RetentionPolicy {
  organizationId: number
  logsRetentionDays: number
  metricsRetentionDays: number
  tracesRetentionDays: number
  /** Null while the organization still uses the platform defaults. */
  updatedAt: string | null
  updatedBy: number | null
  maxLogsRetentionDays: number
  maxMetricsRetentionDays: number
  maxTracesRetentionDays: number
}

export interface UpdateRetentionPolicyInput {
  logsRetentionDays: number
  metricsRetentionDays: number
  tracesRetentionDays: number
}

export interface RetentionPurgeRun {
  id: number
  dataType: RetentionDataType
  startedAt: string
  finishedAt: string
  rowsDeleted: number
  status: PurgeRunStatus
  errorMessage: string | null
}

export const RETENTION_RUNS_LIMIT = 20

export const retentionQueryKeys = {
  policy: (organizationId: number | null) => ['retention', 'policy', organizationId] as const,
  runs: (organizationId: number | null, limit: number) => ['retention', 'runs', organizationId, limit] as const,
}

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getRetentionPolicy(): Promise<RetentionPolicy> {
  return apiRequest<RetentionPolicy>('/v1/organizations/current/retention', { token: authToken() })
}

export function updateRetentionPolicy(input: UpdateRetentionPolicyInput): Promise<RetentionPolicy> {
  return apiRequest<RetentionPolicy>('/v1/organizations/current/retention', {
    method: 'PUT',
    body: input,
    token: authToken(),
  })
}

export function getRetentionPurgeRuns(limit = RETENTION_RUNS_LIMIT): Promise<RetentionPurgeRun[]> {
  return apiRequest<RetentionPurgeRun[]>(`/v1/organizations/current/retention/runs?limit=${limit}`, {
    token: authToken(),
  })
}

export function useRetentionPolicyQuery() {
  const organizationId = useAuthStore((state) => state.currentOrganizationId)
  return useQuery({
    queryKey: retentionQueryKeys.policy(organizationId),
    queryFn: getRetentionPolicy,
    enabled: organizationId !== null,
  })
}

export function useRetentionPurgeRunsQuery(limit = RETENTION_RUNS_LIMIT) {
  const organizationId = useAuthStore((state) => state.currentOrganizationId)
  return useQuery({
    queryKey: retentionQueryKeys.runs(organizationId, limit),
    queryFn: () => getRetentionPurgeRuns(limit),
    enabled: organizationId !== null,
    refetchInterval: 60_000,
  })
}

export function useUpdateRetentionPolicyMutation() {
  const queryClient = useQueryClient()
  const organizationId = useAuthStore((state) => state.currentOrganizationId)
  return useMutation({
    mutationFn: (input: UpdateRetentionPolicyInput) => updateRetentionPolicy(input),
    onSuccess: (policy) => {
      queryClient.setQueryData(retentionQueryKeys.policy(organizationId), policy)
      void queryClient.invalidateQueries({ queryKey: ['retention', 'runs', organizationId] })
    },
  })
}
