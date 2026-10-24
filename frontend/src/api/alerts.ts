import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import type { MembershipRole } from '../store/authStore'
import { apiRequest } from './client'
import type { LogLevel } from './logs'

export type AlertRuleType = 'METRIC_THRESHOLD' | 'HEALTH_CHECK' | 'LOG'
export type AlertCondition = 'GREATER_THAN' | 'GREATER_OR_EQUAL' | 'LESS_THAN' | 'EQUAL'
/** Log alerts compare counts; equality is intentionally not offered. */
export type LogAlertCondition = Exclude<AlertCondition, 'EQUAL'>
export type IncidentStatus = 'FIRING' | 'ACKNOWLEDGED' | 'RESOLVED'
export type NotificationChannelType = 'WEBHOOK' | 'SLACK' | 'EMAIL' | 'PAGERDUTY' | 'TEAMS'
export type LogicOperator = 'AND' | 'OR'
export type NotificationDeliveryStatus = 'PENDING' | 'SUCCESS' | 'FAILED'
export type NotificationEventType =
  | 'INCIDENT_OPENED'
  | 'INCIDENT_ACKNOWLEDGED'
  | 'INCIDENT_RESOLVED'
  | 'SCHEDULED_REPORT'

/** A single condition inside a compound alert rule. */
export interface AlertConditionSpec {
  target: string
  operator: AlertCondition
  threshold: number
}

export interface AlertRule {
  id: number
  organizationId: number
  applicationId: number | null
  name: string
  type: AlertRuleType
  /** Legacy single-condition view; mirrors the first entry of {@link AlertRule.conditions}. */
  target: string
  condition: AlertCondition
  threshold: number
  conditions: AlertConditionSpec[]
  logicOperator: LogicOperator
  durationMinutes: number
  logLevel: LogLevel | null
  logQuery: string | null
  windowMinutes: number | null
  comparison: LogAlertCondition | null
  countThreshold: number | null
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface NotificationChannel {
  id: number
  organizationId: number
  type: NotificationChannelType
  /** Masked destination (scheme and host only); edit requires re-entering the full URL. */
  url: string | null
  slackWebhookUrl: string | null
  emailAddress: string | null
  teamsWebhookUrl: string | null
  /** Masked PagerDuty routing key, when configured. */
  pagerDutyRoutingKey: string | null
  hasWebhookSecret: boolean
  webhookHeaderNames: string[]
  isActive: boolean
}

export interface NotificationDelivery {
  id: number
  channelId: number | null
  channelType: NotificationChannelType | null
  channelDestination: string | null
  incidentId: number | null
  scheduledReportId: number | null
  scheduledReportName: string | null
  eventType: NotificationEventType
  status: NotificationDeliveryStatus
  attempts: number
  lastHttpStatus: number | null
  lastError: string | null
  createdAt: string
  lastAttemptAt: string | null
  test: boolean
}

export interface NotificationDeliveriesPage {
  content: NotificationDelivery[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface NotificationTestResult {
  status: NotificationDeliveryStatus
  message: string
  httpStatus: number | null
  deliveryId: number
}

export interface NotificationDeliveriesQuery {
  channelId?: number
  incidentId?: number
  status?: NotificationDeliveryStatus
  page: number
  size: number
}

export interface AlertIncident {
  id: number
  ruleId: number
  ruleName: string
  ruleType: AlertRuleType
  applicationId: number | null
  logLevel: LogLevel | null
  logQuery: string | null
  windowMinutes: number | null
  comparison: LogAlertCondition | null
  countThreshold: number | null
  status: IncidentStatus
  triggeredAt: string
  acknowledgedBy: number | null
  acknowledgedByName: string | null
  acknowledgedAt: string | null
  resolvedBy: number | null
  resolvedByName: string | null
  resolvedAt: string | null
  notes: string | null
}

export interface IncidentComment {
  id: number
  incidentId: number
  userId: number | null
  username: string | null
  role: MembershipRole | null
  text: string
  /** Platform-generated lifecycle confirmation, rendered separately from human messages. */
  system: boolean
  createdAt: string
  updatedAt: string
}

export interface IncidentCommentsPage {
  content: IncidentComment[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface CreateAlertRuleRequest {
  name: string
  applicationId: number | null
  type: AlertRuleType
  /** Legacy single-condition target; mirrors the first condition. */
  target: string
  condition: AlertCondition
  threshold: number
  conditions: AlertConditionSpec[]
  logicOperator: LogicOperator
  durationMinutes: number
  logLevel?: LogLevel | null
  logQuery?: string | null
  windowMinutes?: number | null
  comparison?: LogAlertCondition | null
  countThreshold?: number | null
  isActive: boolean
}

export type UpdateAlertRuleRequest = Partial<CreateAlertRuleRequest>

export interface LogCountPreviewRequest {
  applicationId: number
  logLevel?: LogLevel | null
  logQuery?: string | null
  windowMinutes: number
}

export interface LogCountPreview {
  count: number
  windowMinutes: number
  from: string
  to: string
}

export interface CreateNotificationChannelRequest {
  type: NotificationChannelType
  url?: string | null
  slackWebhookUrl?: string | null
  emailAddress?: string | null
  teamsWebhookUrl?: string | null
  routingKey?: string | null
  /** HMAC-SHA256 signing secret for generic webhooks; never returned by the API. */
  secret?: string | null
  /** Up to five static headers for generic webhooks. */
  headers?: Record<string, string> | null
  isActive: boolean
}

export type UpdateNotificationChannelRequest = Partial<CreateNotificationChannelRequest>

const RULES_PATH = '/v1/alert-rules'
const CHANNELS_PATH = '/v1/notification-channels'
const DELIVERIES_PATH = '/v1/notification-deliveries'
const INCIDENTS_PATH = '/v1/incidents'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getAlertRules(): Promise<AlertRule[]> {
  return apiRequest<AlertRule[]>(RULES_PATH, { token: authToken() })
}

/**
 * Active metric-threshold rules for one application, optionally narrowed to those referencing
 * `metricName`. Used to overlay alert thresholds on a metric chart; the backend keeps the result
 * scoped to the caller's organization.
 */
export function getMetricAlertRules(applicationId: number, metricName?: string): Promise<AlertRule[]> {
  const params = new URLSearchParams({ applicationId: String(applicationId) })
  if (metricName) {
    params.set('metricName', metricName)
  }
  return apiRequest<AlertRule[]>(`${RULES_PATH}?${params.toString()}`, { token: authToken() })
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

/** Read-only count preview used to calibrate a log rule threshold. */
export function previewLogCount(data: LogCountPreviewRequest): Promise<LogCountPreview> {
  return apiRequest<LogCountPreview>(`${RULES_PATH}/preview-log-count`, {
    method: 'POST',
    body: data,
    token: authToken(),
  })
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

/**
 * Sends one synchronous test notification. The response reports the actual outcome (including the
 * destination's HTTP status), so the UI can show success or a concrete failure reason.
 */
export function testNotificationChannel(id: number): Promise<NotificationTestResult> {
  return apiRequest<NotificationTestResult>(`${CHANNELS_PATH}/${id}/test`, {
    method: 'POST',
    token: authToken(),
  })
}

export function getNotificationDeliveries(
  params: NotificationDeliveriesQuery,
): Promise<NotificationDeliveriesPage> {
  const search = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.channelId !== undefined) {
    search.set('channelId', String(params.channelId))
  }
  if (params.incidentId !== undefined) {
    search.set('incidentId', String(params.incidentId))
  }
  if (params.status) {
    search.set('status', params.status)
  }
  return apiRequest<NotificationDeliveriesPage>(`${DELIVERIES_PATH}?${search.toString()}`, {
    token: authToken(),
  })
}

export function getAlertIncidents(limit?: number, status?: IncidentStatus): Promise<AlertIncident[]> {
  const params = new URLSearchParams()
  if (limit !== undefined) {
    params.set('limit', String(limit))
  }
  if (status) {
    params.set('status', status)
  }
  const query = params.toString() === '' ? '' : `?${params.toString()}`
  return apiRequest<AlertIncident[]>(`${INCIDENTS_PATH}${query}`, { token: authToken() })
}

export function acknowledgeIncident(id: number): Promise<AlertIncident> {
  return apiRequest<AlertIncident>(`${INCIDENTS_PATH}/${id}/acknowledge`, {
    method: 'POST',
    token: authToken(),
  })
}

export function resolveIncident(id: number): Promise<AlertIncident> {
  return apiRequest<AlertIncident>(`${INCIDENTS_PATH}/${id}/resolve`, {
    method: 'POST',
    token: authToken(),
  })
}

export function updateIncidentNotes(id: number, notes: string): Promise<AlertIncident> {
  return apiRequest<AlertIncident>(`${INCIDENTS_PATH}/${id}/notes`, {
    method: 'PUT',
    body: { notes },
    token: authToken(),
  })
}

export function getIncidentComments(
  incidentId: number,
  page = 0,
  size = 50,
): Promise<IncidentCommentsPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<IncidentCommentsPage>(
    `${INCIDENTS_PATH}/${incidentId}/comments?${params.toString()}`,
    { token: authToken() },
  )
}

export function addIncidentComment(incidentId: number, text: string): Promise<IncidentComment> {
  return apiRequest<IncidentComment>(`${INCIDENTS_PATH}/${incidentId}/comments`, {
    method: 'POST',
    body: { text },
    token: authToken(),
  })
}

export const alertRulesQueryKey = ['alert-rules'] as const
export const notificationChannelsQueryKey = ['notification-channels'] as const
export const alertIncidentsQueryKey = (limit: number, status?: IncidentStatus) =>
  ['alert-incidents', limit, status ?? 'ALL'] as const

export function useAlertRulesQuery() {
  return useQuery({ queryKey: alertRulesQueryKey, queryFn: getAlertRules })
}

export const metricAlertRulesQueryKey = (applicationId: number | null, metricName: string | null) =>
  ['alert-rules', 'metric', applicationId ?? null, metricName ?? null] as const

/**
 * Fetches the alert rules that apply to one metric. Disabled until both the application and the
 * metric name are known so the explorer's empty state does not fire a request.
 */
export function useMetricAlertRulesQuery(applicationId: number | null, metricName: string | null) {
  const enabled = applicationId !== null && metricName !== null && metricName !== ''
  return useQuery({
    queryKey: metricAlertRulesQueryKey(applicationId, metricName),
    queryFn: () => getMetricAlertRules(applicationId as number, metricName as string),
    enabled,
  })
}

export const logCountPreviewQueryKey = (filters: LogCountPreviewRequest | null) =>
  ['alert-log-preview', filters] as const

/**
 * Debounced by the caller: send only the settled filter values. Query keys include the filters,
 * so a slow response for an older query can never overwrite the current preview.
 */
export function useLogCountPreviewQuery(filters: LogCountPreviewRequest | null) {
  return useQuery({
    queryKey: logCountPreviewQueryKey(filters),
    queryFn: () => previewLogCount(filters as LogCountPreviewRequest),
    enabled: filters !== null,
    staleTime: 15_000,
    retry: false,
  })
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

export function useTestNotificationChannelMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => testNotificationChannel(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['notification-deliveries'] })
    },
  })
}

export const notificationDeliveriesQueryKey = (params: NotificationDeliveriesQuery | null) =>
  [
    'notification-deliveries',
    params?.channelId ?? null,
    params?.incidentId ?? null,
    params?.status ?? null,
    params?.page ?? null,
    params?.size ?? null,
  ] as const

export function useNotificationDeliveriesQuery(params: NotificationDeliveriesQuery | null) {
  return useQuery({
    queryKey: notificationDeliveriesQueryKey(params),
    queryFn: () => getNotificationDeliveries(params as NotificationDeliveriesQuery),
    enabled: params !== null,
    keepPreviousData: true,
  })
}

function replaceIncidentInCaches(
  queryClient: ReturnType<typeof useQueryClient>,
  updated: AlertIncident,
): void {
  queryClient.setQueriesData<AlertIncident[]>(
    { queryKey: ['alert-incidents'] },
    (current) => current?.map((incident) => (incident.id === updated.id ? updated : incident)),
  )
}

export function useAlertIncidentsQuery(limit = 100, status?: IncidentStatus) {
  return useQuery({
    queryKey: alertIncidentsQueryKey(limit, status),
    queryFn: () => getAlertIncidents(limit, status),
  })
}

export function useAcknowledgeIncidentMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => acknowledgeIncident(id),
    onSuccess: (incident) => {
      replaceIncidentInCaches(queryClient, incident)
      void queryClient.invalidateQueries({ queryKey: ['alert-incidents'] })
    },
  })
}

export function useResolveIncidentMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => resolveIncident(id),
    onSuccess: (incident) => {
      replaceIncidentInCaches(queryClient, incident)
      void queryClient.invalidateQueries({ queryKey: ['alert-incidents'] })
    },
  })
}

export function useUpdateIncidentNotesMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, notes }: { id: number; notes: string }) => updateIncidentNotes(id, notes),
    onSuccess: (incident) => {
      replaceIncidentInCaches(queryClient, incident)
      void queryClient.invalidateQueries({ queryKey: ['alert-incidents'] })
    },
  })
}

export const incidentCommentsQueryKey = (incidentId: number, page: number, size: number) =>
  ['incident-comments', incidentId, page, size] as const

/**
 * War-room thread. Polling is intentional for the MVP: it keeps the thread fresh without a
 * WebSocket and automatically pauses while the browser tab is hidden.
 */
export function useIncidentCommentsQuery(
  incidentId: number | null,
  page = 0,
  size = 50,
  refetchInterval: number | false = false,
) {
  return useQuery({
    queryKey: incidentCommentsQueryKey(incidentId ?? 0, page, size),
    queryFn: () => getIncidentComments(incidentId as number, page, size),
    enabled: incidentId !== null,
    refetchInterval,
    keepPreviousData: true,
  })
}

export function useAddIncidentCommentMutation(incidentId: number | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (text: string) => addIncidentComment(incidentId as number, text),
    onSuccess: () => {
      if (incidentId !== null) {
        void queryClient.invalidateQueries({ queryKey: ['incident-comments', incidentId] })
      }
      // Slash commands (/acknowledge, /resolve) change the incident itself.
      void queryClient.invalidateQueries({ queryKey: ['alert-incidents'] })
    },
  })
}
