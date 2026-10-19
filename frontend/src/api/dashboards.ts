import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export type WidgetType = 'METRIC_CHART' | 'LOG_TABLE' | 'STATUS'

export interface Widget {
  id: number
  dashboardId: number
  type: WidgetType
  config: string
  positionX: number
  positionY: number
  width: number
  height: number
}

export type DashboardVariableType = 'custom' | 'query'

export interface DashboardVariable {
  name: string
  type: DashboardVariableType
  options: string[]
  defaultValue: string
}

export interface Dashboard {
  id: number
  name: string
  organizationId: number
  createdAt: string
  variables: DashboardVariable[]
  widgets: Widget[]
}

export interface DashboardLayout {
  positionX: number
  positionY: number
  width: number
  height: number
}

export interface CreateWidgetRequest extends DashboardLayout {
  type: WidgetType
  config: string
}

export interface UpdateWidgetRequest extends Partial<DashboardLayout> {
  type?: WidgetType
  config?: string
}

export interface UpdateDashboardRequest {
  name: string
  variables?: DashboardVariable[]
}

const BASE_PATH = '/v1/dashboards'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getDashboards(): Promise<Dashboard[]> {
  return apiRequest<Dashboard[]>(BASE_PATH, { token: authToken() })
}

export function getDashboard(id: number): Promise<Dashboard> {
  return apiRequest<Dashboard>(`${BASE_PATH}/${id}`, { token: authToken() })
}

export function createDashboard(name: string): Promise<Dashboard> {
  return apiRequest<Dashboard>(BASE_PATH, { method: 'POST', body: { name }, token: authToken() })
}

export function renameDashboard(id: number, name: string): Promise<Dashboard> {
  return apiRequest<Dashboard>(`${BASE_PATH}/${id}`, { method: 'PUT', body: { name }, token: authToken() })
}

export function updateDashboard(id: number, data: UpdateDashboardRequest): Promise<Dashboard> {
  return apiRequest<Dashboard>(`${BASE_PATH}/${id}`, { method: 'PUT', body: data, token: authToken() })
}

export function deleteDashboard(id: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}`, { method: 'DELETE', token: authToken() })
}

export function addWidget(dashboardId: number, data: CreateWidgetRequest): Promise<Widget> {
  return apiRequest<Widget>(`${BASE_PATH}/${dashboardId}/widgets`, {
    method: 'POST',
    body: data,
    token: authToken(),
  })
}

export function updateWidget(dashboardId: number, widgetId: number, data: UpdateWidgetRequest): Promise<Widget> {
  return apiRequest<Widget>(`${BASE_PATH}/${dashboardId}/widgets/${widgetId}`, {
    method: 'PUT',
    body: data,
    token: authToken(),
  })
}

export function deleteWidget(dashboardId: number, widgetId: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${dashboardId}/widgets/${widgetId}`, {
    method: 'DELETE',
    token: authToken(),
  })
}

export const dashboardsQueryKey = ['dashboards'] as const
export const dashboardQueryKey = (id: number | null) => ['dashboard', id ?? null] as const

export function useDashboardsQuery() {
  return useQuery({
    queryKey: dashboardsQueryKey,
    queryFn: getDashboards,
  })
}

export function useDashboardQuery(id: number | null) {
  return useQuery({
    queryKey: dashboardQueryKey(id),
    queryFn: () => getDashboard(id as number),
    enabled: id !== null,
  })
}

function useDashboardInvalidation(id: number | null) {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: dashboardsQueryKey })
    if (id !== null) {
      void queryClient.invalidateQueries({ queryKey: dashboardQueryKey(id) })
    }
  }
}

export function useCreateDashboardMutation() {
  const invalidate = useDashboardInvalidation(null)
  return useMutation({
    mutationFn: (name: string) => createDashboard(name),
    onSuccess: invalidate,
  })
}

export function useRenameDashboardMutation(dashboardId: number | null) {
  const invalidate = useDashboardInvalidation(dashboardId)
  return useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) => renameDashboard(id, name),
    onSuccess: invalidate,
  })
}

export function useUpdateDashboardMutation(dashboardId: number | null) {
  const invalidate = useDashboardInvalidation(dashboardId)
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateDashboardRequest }) => updateDashboard(id, data),
    onSuccess: invalidate,
  })
}

export function useDeleteDashboardMutation() {
  const invalidate = useDashboardInvalidation(null)
  return useMutation({
    mutationFn: (id: number) => deleteDashboard(id),
    onSuccess: invalidate,
  })
}

export function useAddWidgetMutation(dashboardId: number) {
  const invalidate = useDashboardInvalidation(dashboardId)
  return useMutation({
    mutationFn: (data: CreateWidgetRequest) => addWidget(dashboardId, data),
    onSuccess: invalidate,
  })
}

export function useUpdateWidgetMutation(dashboardId: number) {
  const invalidate = useDashboardInvalidation(dashboardId)
  return useMutation({
    mutationFn: ({ widgetId, data }: { widgetId: number; data: UpdateWidgetRequest }) =>
      updateWidget(dashboardId, widgetId, data),
    onSuccess: invalidate,
  })
}

export function useDeleteWidgetMutation(dashboardId: number) {
  const invalidate = useDashboardInvalidation(dashboardId)
  return useMutation({
    mutationFn: (widgetId: number) => deleteWidget(dashboardId, widgetId),
    onSuccess: invalidate,
  })
}
