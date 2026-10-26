import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export type ResourceSearchType =
  | 'applications'
  | 'dashboards'
  | 'alertRules'
  | 'healthChecks'
  | 'savedViews'
  | 'incidents'

export interface ResourceSearchResult {
  type: ResourceSearchType
  id: number
  title: string
  subtitle: string
}

interface ResourceSearchResponse {
  results: ResourceSearchResult[]
}

export interface ResourceSearchParams {
  query: string
  limit?: number
  signal?: AbortSignal
}

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function searchResources({ query, limit = 5, signal }: ResourceSearchParams): Promise<ResourceSearchResponse> {
  const params = new URLSearchParams({
    q: query,
    limit: String(limit),
  })
  return apiRequest<ResourceSearchResponse>(`/v1/search?${params.toString()}`, {
    token: authToken(),
    signal,
  })
}

export const resourceSearchQueryKey = (organizationId: number | null, query: string) =>
  ['resource-search', organizationId, query] as const

export function useResourceSearchQuery(query: string, enabled: boolean) {
  const organizationId = useAuthStore((state) => state.currentOrganizationId)
  const trimmed = query.trim()
  return useQuery({
    queryKey: resourceSearchQueryKey(organizationId, trimmed),
    queryFn: ({ signal }) => searchResources({ query: trimmed, signal }),
    enabled: enabled && trimmed.length >= 2,
    staleTime: 30_000,
    retry: false,
  })
}
