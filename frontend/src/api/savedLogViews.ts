import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'
import type { LogLevel } from './logs'

/** The persisted filter set of a saved view (mirrors the backend SavedLogViewQuery contract). */
export interface SavedLogViewQuery {
  applicationId: number
  level?: LogLevel
  searchText?: string
  timeRange: { from: string; to: string }
}

export interface SavedLogView {
  id: number
  name: string
  queryParams: SavedLogViewQuery
  createdAt: string
  updatedAt: string
}

export interface CreateSavedLogViewRequest {
  name: string
  queryParams: SavedLogViewQuery
}

const BASE_PATH = '/v1/saved-log-views'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getSavedLogViews(): Promise<SavedLogView[]> {
  return apiRequest<SavedLogView[]>(BASE_PATH, { token: authToken() })
}

export function createSavedLogView(data: CreateSavedLogViewRequest): Promise<SavedLogView> {
  return apiRequest<SavedLogView>(BASE_PATH, { method: 'POST', body: data, token: authToken() })
}

export function renameSavedLogView(id: number, name: string): Promise<SavedLogView> {
  return apiRequest<SavedLogView>(`${BASE_PATH}/${id}`, {
    method: 'PUT',
    body: { name },
    token: authToken(),
  })
}

export function deleteSavedLogView(id: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}`, { method: 'DELETE', token: authToken() })
}

export const savedLogViewsQueryKey = ['saved-log-views'] as const

export function useSavedLogViewsQuery() {
  return useQuery({
    queryKey: savedLogViewsQueryKey,
    queryFn: getSavedLogViews,
  })
}

function useSavedLogViewsInvalidation() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: savedLogViewsQueryKey })
}

export function useCreateSavedLogViewMutation() {
  const invalidate = useSavedLogViewsInvalidation()
  return useMutation({
    mutationFn: (data: CreateSavedLogViewRequest) => createSavedLogView(data),
    onSuccess: invalidate,
  })
}

export function useRenameSavedLogViewMutation() {
  const invalidate = useSavedLogViewsInvalidation()
  return useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) => renameSavedLogView(id, name),
    onSuccess: invalidate,
  })
}

export function useDeleteSavedLogViewMutation() {
  const invalidate = useSavedLogViewsInvalidation()
  return useMutation({
    mutationFn: (id: number) => deleteSavedLogView(id),
    onSuccess: invalidate,
  })
}
