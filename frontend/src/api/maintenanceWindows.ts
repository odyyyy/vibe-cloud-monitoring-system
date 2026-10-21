import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export type MaintenanceScopeType = 'GLOBAL' | 'APPLICATION' | 'SPECIFIC_RULE'

export interface MaintenanceWindow {
  id: number
  organizationId: number
  name: string
  scopeType: MaintenanceScopeType
  scopeId: number | null
  startTime: string
  endTime: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateMaintenanceWindowRequest {
  name: string
  scopeType: MaintenanceScopeType
  scopeId: number | null
  startTime: string
  endTime: string
  isActive?: boolean
}

export type UpdateMaintenanceWindowRequest = Partial<CreateMaintenanceWindowRequest>

const BASE_PATH = '/v1/maintenance-windows'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getMaintenanceWindows(): Promise<MaintenanceWindow[]> {
  return apiRequest<MaintenanceWindow[]>(BASE_PATH, { token: authToken() })
}

export function createMaintenanceWindow(
  data: CreateMaintenanceWindowRequest,
): Promise<MaintenanceWindow> {
  return apiRequest<MaintenanceWindow>(BASE_PATH, { method: 'POST', body: data, token: authToken() })
}

export function updateMaintenanceWindow(
  id: number,
  data: UpdateMaintenanceWindowRequest,
): Promise<MaintenanceWindow> {
  return apiRequest<MaintenanceWindow>(`${BASE_PATH}/${id}`, {
    method: 'PUT',
    body: data,
    token: authToken(),
  })
}

export function deleteMaintenanceWindow(id: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}`, { method: 'DELETE', token: authToken() })
}

export const maintenanceWindowsQueryKey = ['maintenance-windows'] as const

/**
 * Loads the tenant's maintenance windows. The short refetch interval keeps the active-window
 * indicator on the Alerts/Incidents pages current without polling aggressively.
 */
export function useMaintenanceWindowsQuery(refetchInterval: number | false = 30_000) {
  return useQuery({
    queryKey: maintenanceWindowsQueryKey,
    queryFn: getMaintenanceWindows,
    refetchInterval,
  })
}

export function useCreateMaintenanceWindowMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateMaintenanceWindowRequest) => createMaintenanceWindow(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: maintenanceWindowsQueryKey })
    },
  })
}

export function useUpdateMaintenanceWindowMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateMaintenanceWindowRequest }) =>
      updateMaintenanceWindow(id, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: maintenanceWindowsQueryKey })
    },
  })
}

export function useDeleteMaintenanceWindowMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteMaintenanceWindow(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: maintenanceWindowsQueryKey })
    },
  })
}
