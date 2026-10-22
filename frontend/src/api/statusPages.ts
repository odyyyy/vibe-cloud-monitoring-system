import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest, ApiError } from './client'

export type StatusPageComponentStatus = 'UP' | 'DEGRADED' | 'DOWN' | 'UNKNOWN'

export interface StatusPageComponent {
  id: number
  name: string
  sortOrder: number
  linkedHealthCheckId: number | null
}

export interface StatusPage {
  id: number
  organizationId: number
  slug: string
  title: string
  description: string | null
  isActive: boolean
  components: StatusPageComponent[]
  createdAt: string
  updatedAt: string
}

export interface StatusPageComponentInput {
  name: string
  linkedHealthCheckId: number | null
}

export interface StatusPageInput {
  slug: string
  title: string
  description: string | null
  isActive: boolean
  components: StatusPageComponentInput[]
}

export interface PublicStatusPageComponent {
  name: string
  status: StatusPageComponentStatus
}

export interface PublicStatusPageOutage {
  componentName: string
  title: string
  startedAt: string
  resolvedAt: string | null
}

export interface PublicStatusPage {
  title: string
  description: string | null
  overallStatus: StatusPageComponentStatus
  components: PublicStatusPageComponent[]
  updatedAt: string
  recentOutages: PublicStatusPageOutage[]
}

const BASE_PATH = '/v1/status-pages'
const PUBLIC_BASE_PATH = '/v1/public/status-pages'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getStatusPages(): Promise<StatusPage[]> {
  return apiRequest<StatusPage[]>(BASE_PATH, { token: authToken() })
}

export function createStatusPage(data: StatusPageInput): Promise<StatusPage> {
  return apiRequest<StatusPage>(BASE_PATH, { method: 'POST', body: data, token: authToken() })
}

export function updateStatusPage(id: number, data: StatusPageInput): Promise<StatusPage> {
  return apiRequest<StatusPage>(`${BASE_PATH}/${id}`, { method: 'PUT', body: data, token: authToken() })
}

export function deleteStatusPage(id: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}`, { method: 'DELETE', token: authToken() })
}

export async function getPublicStatusPage(slug: string): Promise<PublicStatusPage | null> {
  try {
    return await apiRequest<PublicStatusPage>(`${PUBLIC_BASE_PATH}/${encodeURIComponent(slug)}`)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null
    }
    throw error
  }
}

export const statusPagesQueryKey = ['status-pages'] as const
export const publicStatusPageQueryKey = (slug: string | null) =>
  ['status-pages', 'public', slug ?? null] as const

export function useStatusPagesQuery() {
  return useQuery({ queryKey: statusPagesQueryKey, queryFn: getStatusPages })
}

export function usePublicStatusPageQuery(slug: string | null, refetchInterval: number | false) {
  return useQuery({
    queryKey: publicStatusPageQueryKey(slug),
    queryFn: () => getPublicStatusPage(slug as string),
    enabled: slug !== null && slug.length > 0,
    retry: false,
    refetchInterval,
  })
}

function useInvalidateStatusPages() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: statusPagesQueryKey })
  }
}

export function useCreateStatusPageMutation() {
  const invalidate = useInvalidateStatusPages()
  return useMutation({
    mutationFn: (data: StatusPageInput) => createStatusPage(data),
    onSuccess: invalidate,
  })
}

export function useUpdateStatusPageMutation() {
  const invalidate = useInvalidateStatusPages()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: StatusPageInput }) => updateStatusPage(id, data),
    onSuccess: invalidate,
  })
}

export function useDeleteStatusPageMutation() {
  const invalidate = useInvalidateStatusPages()
  return useMutation({
    mutationFn: (id: number) => deleteStatusPage(id),
    onSuccess: invalidate,
  })
}
