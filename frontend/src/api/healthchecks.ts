import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { mapWithConcurrency } from '../lib/concurrency'
import { apiRequest } from './client'

export type HealthStatus = 'UP' | 'DOWN' | 'TIMEOUT' | 'UNKNOWN'
export type HealthCheckMethod = 'GET' | 'POST'

export interface HealthCheckConfig {
  id: number
  organizationId: number
  name: string
  targetUrl: string
  method: HealthCheckMethod
  expectedStatusCode: number
  intervalSeconds: number
  isActive: boolean
}

export interface HealthCheckResult {
  id: number
  configId: number
  status: HealthStatus
  responseTimeMs: number
  errorMessage?: string | null
  checkedAt: string
}

export type CreateHealthCheckRequest = Omit<HealthCheckConfig, 'id' | 'organizationId'>
export type UpdateHealthCheckRequest = Partial<Omit<HealthCheckConfig, 'id' | 'organizationId'>>

/** A check together with its most recent result (null when it has never run). */
export interface HealthCheckOverview {
  check: HealthCheckConfig
  latest: HealthCheckResult | null
}

const BASE_PATH = '/v1/health-checks'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getHealthChecks(): Promise<HealthCheckConfig[]> {
  return apiRequest<HealthCheckConfig[]>(BASE_PATH, { token: authToken() })
}

export function createHealthCheck(data: CreateHealthCheckRequest): Promise<HealthCheckConfig> {
  return apiRequest<HealthCheckConfig>(BASE_PATH, { method: 'POST', body: data, token: authToken() })
}

export function updateHealthCheck(id: number, data: UpdateHealthCheckRequest): Promise<HealthCheckConfig> {
  return apiRequest<HealthCheckConfig>(`${BASE_PATH}/${id}`, { method: 'PUT', body: data, token: authToken() })
}

export function deleteHealthCheck(id: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}`, { method: 'DELETE', token: authToken() })
}

export function getHealthCheckResults(configId: number, limit?: number): Promise<HealthCheckResult[]> {
  const query = limit === undefined ? '' : `?limit=${encodeURIComponent(String(limit))}`
  return apiRequest<HealthCheckResult[]>(`${BASE_PATH}/${configId}/results${query}`, { token: authToken() })
}

export const healthChecksQueryKey = ['health-checks'] as const
export const healthChecksListQueryKey = ['health-checks', 'list'] as const
export const healthCheckResultsQueryKey = (configId: number | null, limit: number) =>
  ['health-checks', configId ?? null, 'results', limit] as const

/** Loads the bare list of checks (no per-check result lookups) for selection inputs. */
export function useHealthChecksListQuery() {
  return useQuery({ queryKey: healthChecksListQueryKey, queryFn: getHealthChecks })
}

/**
 * Loads every check together with its latest result. The list endpoint does not include
 * status, so one `results?limit=1` request is issued per check. Requests are bounded by
 * {@link OVERVIEW_CONCURRENCY} so a large account cannot flood the backend. A failure to load
 * a single check's latest result degrades that row to UNKNOWN instead of failing the page.
 */
const OVERVIEW_CONCURRENCY = 4

async function getHealthChecksOverview(): Promise<HealthCheckOverview[]> {
  const checks = await getHealthChecks()
  return mapWithConcurrency(checks, OVERVIEW_CONCURRENCY, async (check) => {
    try {
      const results = await getHealthCheckResults(check.id, 1)
      return { check, latest: results[0] ?? null }
    } catch {
      return { check, latest: null }
    }
  })
}

export function useHealthChecksOverviewQuery(refetchInterval: number | false) {
  return useQuery({
    queryKey: healthChecksQueryKey,
    queryFn: getHealthChecksOverview,
    refetchInterval,
  })
}

export function useHealthCheckResultsQuery(configId: number | null, limit = 50) {
  return useQuery({
    queryKey: healthCheckResultsQueryKey(configId, limit),
    queryFn: () => getHealthCheckResults(configId as number, limit),
    enabled: configId !== null,
  })
}

export function useCreateHealthCheckMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateHealthCheckRequest) => createHealthCheck(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: healthChecksQueryKey })
    },
  })
}

/**
 * Updates a check. When the active flag is part of the payload the list cache is updated
 * optimistically and rolled back if the request fails.
 */
export function useUpdateHealthCheckMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateHealthCheckRequest }) =>
      updateHealthCheck(id, data),
    onMutate: async ({ id, data }) => {
      await queryClient.cancelQueries({ queryKey: healthChecksQueryKey })
      const previous = queryClient.getQueryData<HealthCheckOverview[]>(healthChecksQueryKey)
      if (previous && data.isActive !== undefined) {
        queryClient.setQueryData<HealthCheckOverview[]>(
          healthChecksQueryKey,
          previous.map((entry) =>
            entry.check.id === id ? { ...entry, check: { ...entry.check, isActive: data.isActive as boolean } } : entry,
          ),
        )
      }
      return { previous }
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(healthChecksQueryKey, context.previous)
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: healthChecksQueryKey })
    },
  })
}

export function useDeleteHealthCheckMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteHealthCheck(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: healthChecksQueryKey })
    },
  })
}
