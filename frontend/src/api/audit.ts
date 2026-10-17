import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export interface AuditEventsQueryParams {
  action?: string
  from?: string
  to?: string
  page: number
  size: number
}

export interface AuditEvent {
  id: number
  userId: number
  username: string | null
  action: string
  resourceId: string | null
  ipAddress: string | null
  timestamp: string
}

export interface AuditEventsPageResponse {
  content: AuditEvent[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getAuditEvents(params: AuditEventsQueryParams): Promise<AuditEventsPageResponse> {
  const search = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
  })
  if (params.action) {
    search.set('action', params.action)
  }
  if (params.from) {
    search.set('from', params.from)
  }
  if (params.to) {
    search.set('to', params.to)
  }

  return apiRequest<AuditEventsPageResponse>(`/v1/audit-events?${search.toString()}`, { token: authToken() })
}

export const auditEventsQueryKey = (params: AuditEventsQueryParams | null) =>
  [
    'audit-events',
    'query',
    params?.action ?? null,
    params?.from ?? null,
    params?.to ?? null,
    params?.page ?? null,
    params?.size ?? null,
  ] as const

export function useAuditEventsQuery(params: AuditEventsQueryParams | null) {
  return useQuery({
    queryKey: auditEventsQueryKey(params),
    queryFn: () => getAuditEvents(params as AuditEventsQueryParams),
    enabled: params !== null,
    keepPreviousData: true,
  })
}
