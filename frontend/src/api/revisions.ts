import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export type ConfigResourceKind = 'dashboard' | 'alert-rule'
export type ConfigChangeType = 'BASELINE' | 'CREATE' | 'UPDATE' | 'RESTORE'

export interface ConfigRevisionSummary {
  revisionNumber: number
  userDisplayName: string | null
  changeType: ConfigChangeType
  createdAt: string
  restoredFromRevisionNumber: number | null
}

export interface ConfigRevision extends ConfigRevisionSummary {
  schemaVersion: number
  snapshot: string
}

export interface RevisionsPage {
  content: ConfigRevisionSummary[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

const BASE_PATHS: Record<ConfigResourceKind, string> = {
  dashboard: '/v1/dashboards',
  'alert-rule': '/v1/alert-rules',
}

function authToken(): string | null {
  return useAuthStore.getState().token
}

function revisionsPath(kind: ConfigResourceKind, resourceId: number): string {
  return `${BASE_PATHS[kind]}/${resourceId}/revisions`
}

export function getRevisions(
  kind: ConfigResourceKind,
  resourceId: number,
  page: number,
  size: number,
): Promise<RevisionsPage> {
  const search = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<RevisionsPage>(`${revisionsPath(kind, resourceId)}?${search.toString()}`, {
    token: authToken(),
  })
}

export function getRevision(
  kind: ConfigResourceKind,
  resourceId: number,
  revisionNumber: number,
): Promise<ConfigRevision> {
  return apiRequest<ConfigRevision>(`${revisionsPath(kind, resourceId)}/${revisionNumber}`, {
    token: authToken(),
  })
}

export function restoreRevision(
  kind: ConfigResourceKind,
  resourceId: number,
  revisionNumber: number,
): Promise<unknown> {
  return apiRequest<unknown>(`${revisionsPath(kind, resourceId)}/${revisionNumber}/restore`, {
    method: 'POST',
    token: authToken(),
  })
}

export const revisionsQueryKey = (
  kind: ConfigResourceKind,
  resourceId: number,
  page: number,
  size: number,
) => ['config-revisions', kind, resourceId, page, size] as const

export const revisionQueryKey = (
  kind: ConfigResourceKind,
  resourceId: number,
  revisionNumber: number | null,
) => ['config-revision', kind, resourceId, revisionNumber] as const

export function useRevisionsQuery(
  kind: ConfigResourceKind,
  resourceId: number,
  page: number,
  size: number,
  enabled: boolean,
) {
  return useQuery({
    queryKey: revisionsQueryKey(kind, resourceId, page, size),
    queryFn: () => getRevisions(kind, resourceId, page, size),
    enabled,
    keepPreviousData: true,
  })
}

export function useRevisionQuery(
  kind: ConfigResourceKind,
  resourceId: number,
  revisionNumber: number | null,
  enabled: boolean,
) {
  return useQuery({
    queryKey: revisionQueryKey(kind, resourceId, revisionNumber),
    queryFn: () => getRevision(kind, resourceId, revisionNumber as number),
    enabled: enabled && revisionNumber !== null,
  })
}

export function useRestoreRevisionMutation(kind: ConfigResourceKind, resourceId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (revisionNumber: number) => restoreRevision(kind, resourceId, revisionNumber),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['config-revisions', kind, resourceId] })
      void queryClient.invalidateQueries({ queryKey: ['config-revision', kind, resourceId] })
    },
  })
}
