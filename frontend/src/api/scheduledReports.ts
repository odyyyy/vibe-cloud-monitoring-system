import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from './client'
import type { NotificationDeliveryStatus } from './alerts'
import { useAuthStore } from '../store/authStore'

export type ScheduledReportFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY'
export type ScheduledReportChannelType = 'EMAIL' | 'SLACK'

export interface ScheduledReport {
  id: number
  organizationId: number
  name: string
  frequency: ScheduledReportFrequency
  dayOfWeek: number | null
  timeOfDay: string
  timeZone: string
  channelType: ScheduledReportChannelType
  channelId: number | null
  recipients: string[]
  includeMetrics: boolean
  includeTopErrors: boolean
  includeUptime: boolean
  lastRunAt: string | null
  nextRunAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ScheduledReportRequest {
  name: string
  frequency: ScheduledReportFrequency
  dayOfWeek: number | null
  timeOfDay: string
  channelType: ScheduledReportChannelType
  channelId: number | null
  recipients: string[]
  includeMetrics: boolean
  includeTopErrors: boolean
  includeUptime: boolean
}

export interface ScheduledReportUptime {
  upChecks: number
  totalChecks: number
  percentage: number | null
}

export interface ScheduledReportCountedMessage {
  message: string
  count: number
}

export interface ScheduledReportCountedName {
  name: string
  count: number
}

export interface ScheduledReportIncident {
  ruleName: string
  status: string
  triggeredAt: string
  resolvedAt: string | null
}

export interface ScheduledReportContent {
  reportName: string
  frequency: ScheduledReportFrequency
  from: string
  to: string
  generatedAt: string
  applicationCount: number
  healthCheckCount: number
  uptime: ScheduledReportUptime | null
  errors: {
    totalErrors: number
    topErrors: ScheduledReportCountedMessage[]
  } | null
  metrics: {
    totalPoints: number
    distinctMetrics: number
    topMetrics: ScheduledReportCountedName[]
  } | null
  incidents: {
    totalIncidents: number
    unresolvedIncidents: number
    latest: ScheduledReportIncident[]
  } | null
}

export interface ScheduledReportPreview {
  reportId: number
  reportName: string
  channelType: ScheduledReportChannelType
  deliveryStatus: NotificationDeliveryStatus
  deliveryMessage: string
  deliveryId: number
  lastHttpStatus: number | null
  subject: string
  html: string
  slackMessage: Record<string, unknown> | null
  content: ScheduledReportContent
}

const BASE_PATH = '/v1/reports'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getScheduledReports(): Promise<ScheduledReport[]> {
  return apiRequest<ScheduledReport[]>(BASE_PATH, { token: authToken() })
}

export function createScheduledReport(
  data: ScheduledReportRequest,
): Promise<ScheduledReport> {
  return apiRequest<ScheduledReport>(BASE_PATH, {
    method: 'POST',
    body: data,
    token: authToken(),
  })
}

export function updateScheduledReport(
  id: number,
  data: ScheduledReportRequest,
): Promise<ScheduledReport> {
  return apiRequest<ScheduledReport>(`${BASE_PATH}/${id}`, {
    method: 'PUT',
    body: data,
    token: authToken(),
  })
}

export function deleteScheduledReport(id: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}`, { method: 'DELETE', token: authToken() })
}

export function testScheduledReport(id: number): Promise<ScheduledReportPreview> {
  return apiRequest<ScheduledReportPreview>(`${BASE_PATH}/${id}/test`, {
    method: 'POST',
    token: authToken(),
  })
}

export const scheduledReportsQueryKey = ['scheduled-reports'] as const

export function useScheduledReportsQuery() {
  return useQuery({
    queryKey: scheduledReportsQueryKey,
    queryFn: getScheduledReports,
  })
}

export function useCreateScheduledReportMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: ScheduledReportRequest) => createScheduledReport(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: scheduledReportsQueryKey })
    },
  })
}

export function useUpdateScheduledReportMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: ScheduledReportRequest }) =>
      updateScheduledReport(id, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: scheduledReportsQueryKey })
    },
  })
}

export function useDeleteScheduledReportMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteScheduledReport(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: scheduledReportsQueryKey })
    },
  })
}

export function useTestScheduledReportMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => testScheduledReport(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: scheduledReportsQueryKey })
    },
  })
}
