import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export type AlertRuleType = 'METRIC_THRESHOLD' | 'HEALTH_CHECK'
export type AlertCondition = 'GREATER_THAN' | 'LESS_THAN' | 'EQUAL'
export type IncidentStatus = 'FIRING' | 'RESOLVED'
export type NotificationChannelType = 'WEBHOOK'

export interface AlertRule {
  id: number
  organizationId: number
  applicationId: number | null
  name: string
  type: AlertRuleType
  target: string
  condition: AlertCondition
  threshold: number
  durationMinutes: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface NotificationChannel {
  id: number
  organizationId: number
  type: NotificationChannelType
  url: string
  isActive: boolean
}

export interface AlertIncident {
  id: number
  ruleId: number
  ruleName: string
  status: IncidentStatus
  triggeredAt: string
  resolvedAt: string | null
}

export interface CreateAlertRuleRequest {
  name: string
  applicationId: number | null
  type: AlertRuleType
  target: string
  condition: AlertCondition
  threshold: number
  durationMinutes: number
  isActive: boolean
}

export type UpdateAlertRuleRequest = Partial<CreateAlertRuleRequest>

export interface CreateNotificationChannelRequest {
  type: NotificationChannelType
  url: string
  isActive: boolean
}

export type UpdateNotificationChannelRequest = Partial<CreateNotificationChannelRequest>

const RULES_PATH = '/v1/alert-rules'
const CHANNELS_PATH = '/v1/notification-channels'
const INCIDENTS_PATH = '/v1/alert-incidents'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getAlertRules(): Promise<AlertRule[]> {
  return apiRequest<AlertRule[]>(RULES_PATH, { token: authToken() })
}

export function createAlertRule(data: CreateAlertRuleRequest): Promise<AlertRule> {
  return apiRequest<AlertRule>(RULES_PATH, { method: 'POST', body: data, token: authToken() })
}

export function updateAlertRule(id: number, data: UpdateAlertRuleRequest): Promise<AlertRule> {
  return apiRequest<AlertRule>(`${RULES_PATH}/${id}`, { method: 'PUT', body: data, token: authToken() })
}

export function deleteAlertRule(id: number): Promise<void> {
  return apiRequest<void>(`${RULES_PATH}/${id}`, { method: 'DELETE', token: authToken() })
}

export function getNotificationChannels(): Promise<NotificationChannel[]> {
  return apiRequest<NotificationChannel[]>(CHANNELS_PATH, { token: authToken() })
}

export function createNotificationChannel(data: CreateNotificationChannelRequest): Promise<NotificationChannel> {
  return apiRequest<NotificationChannel>(CHANNELS_PATH, { method: 'POST', body: data, token: authToken() })
}

export function updateNotificationChannel(id: number, data: UpdateNotificationChannelRequest): Promise<NotificationChannel> {
  return apiRequest<NotificationChannel>(`${CHANNELS_PATH}/${id}`, { method: 'PUT', body: data, token: authToken() })
}

export function deleteNotificationChannel(id: number): Promise<void> {
  return apiRequest<void>(`${CHANNELS_PATH}/${id}`, { method: 'DELETE', token: authToken() })
}

export function getAlertIncidents(limit?: number): Promise<AlertIncident[]> {
  const query = limit === undefined ? '' : `?limit=${encodeURIComponent(String(limit))}`
  return apiRequest<AlertIncident[]>(`${INCIDENTS_PATH}${query}`, { token: authToken() })
}

export const alertRulesQueryKey = ['alert-rules'] as const
export const notificationChannelsQueryKey = ['notification-channels'] as const
export const alertIncidentsQueryKey = (limit: number) => ['alert-incidents', limit] as const

export function useAlertRulesQuery() {
  return useQuery({ queryKey: alertRulesQueryKey, queryFn: getAlertRules })
}

export function useCreateAlertRuleMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateAlertRuleRequest) => createAlertRule(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: alertRulesQueryKey })
    },
  })
}

/**
 * Updates a rule. When the active flag is part of the payload the list cache is updated
 * optimistically and rolled back if the request fails.
 */
export function useUpdateAlertRuleMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateAlertRuleRequest }) => updateAlertRule(id, data),
    onMutate: async ({ id, data }) => {
      await queryClient.cancelQueries({ queryKey: alertRulesQueryKey })
      const previous = queryClient.getQueryData<AlertRule[]>(alertRulesQueryKey)
      if (previous && data.isActive !== undefined) {
        queryClient.setQueryData<AlertRule[]>(
          alertRulesQueryKey,
          previous.map((rule) => (rule.id === id ? { ...rule, isActive: data.isActive as boolean } : rule)),
        )
      }
      return { previous }
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(alertRulesQueryKey, context.previous)
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: alertRulesQueryKey })
    },
  })
}

export function useDeleteAlertRuleMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteAlertRule(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: alertRulesQueryKey })
      void queryClient.invalidateQueries({ queryKey: ['alert-incidents'] })
    },
  })
}

export function useNotificationChannelsQuery() {
  return useQuery({ queryKey: notificationChannelsQueryKey, queryFn: getNotificationChannels })
}

export function useCreateNotificationChannelMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateNotificationChannelRequest) => createNotificationChannel(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationChannelsQueryKey })
    },
  })
}

export function useUpdateNotificationChannelMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateNotificationChannelRequest }) =>
      updateNotificationChannel(id, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationChannelsQueryKey })
    },
  })
}

export function useDeleteNotificationChannelMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteNotificationChannel(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationChannelsQueryKey })
    },
  })
}

export function useAlertIncidentsQuery(limit = 100) {
  return useQuery({ queryKey: alertIncidentsQueryKey(limit), queryFn: () => getAlertIncidents(limit) })
}
