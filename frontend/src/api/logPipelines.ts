import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'
import type { LogLevel } from './logs'

export const LOG_PIPELINE_ACTIONS = ['DROP', 'PARSE_REGEX', 'ADD_ATTRIBUTE', 'MASK'] as const
export type LogPipelineAction = (typeof LOG_PIPELINE_ACTIONS)[number]

export interface LogPipelineRule {
  id: number
  organizationId: number
  applicationId: number | null
  name: string
  filter: string
  action: LogPipelineAction
  actionConfig: Record<string, unknown> | null
  orderIndex: number
  extractedFields: string[]
  createdAt: string
  updatedAt: string
}

export interface SaveLogPipelineRuleRequest {
  name: string
  applicationId: number | null
  filter: string
  action: LogPipelineAction
  actionConfig: Record<string, unknown>
}

export interface TestLogPipelineRequest {
  applicationId: number
  level: LogLevel
  message: string
  context?: string
}

export interface LogPipelineMatchedRule {
  id: number
  name: string
  action: LogPipelineAction
}

export interface LogPipelineTestOutput {
  level: LogLevel
  message: string
  context: string | null
}

export interface LogPipelineTestResponse {
  dropped: boolean
  matchedRules: LogPipelineMatchedRule[]
  output: LogPipelineTestOutput | null
}

const BASE_PATH = '/v1/log-pipelines'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getLogPipelineRules(): Promise<LogPipelineRule[]> {
  return apiRequest<LogPipelineRule[]>(BASE_PATH, { token: authToken() })
}

export function createLogPipelineRule(data: SaveLogPipelineRuleRequest): Promise<LogPipelineRule> {
  return apiRequest<LogPipelineRule>(BASE_PATH, { method: 'POST', body: data, token: authToken() })
}

export function updateLogPipelineRule(
  id: number,
  data: SaveLogPipelineRuleRequest,
): Promise<LogPipelineRule> {
  return apiRequest<LogPipelineRule>(`${BASE_PATH}/${id}`, {
    method: 'PUT',
    body: data,
    token: authToken(),
  })
}

export function deleteLogPipelineRule(id: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}`, { method: 'DELETE', token: authToken() })
}

export function reorderLogPipelineRules(ids: number[]): Promise<LogPipelineRule[]> {
  return apiRequest<LogPipelineRule[]>(`${BASE_PATH}/reorder`, {
    method: 'POST',
    body: { ids },
    token: authToken(),
  })
}

export function testLogPipeline(data: TestLogPipelineRequest): Promise<LogPipelineTestResponse> {
  return apiRequest<LogPipelineTestResponse>(`${BASE_PATH}/test`, {
    method: 'POST',
    body: data,
    token: authToken(),
  })
}

export const logPipelineRulesQueryKey = ['log-pipelines'] as const

export function useLogPipelineRulesQuery(enabled = true) {
  return useQuery({
    queryKey: logPipelineRulesQueryKey,
    queryFn: getLogPipelineRules,
    enabled,
  })
}

function useInvalidateRules() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: logPipelineRulesQueryKey })
}

export function useCreateLogPipelineRuleMutation() {
  const invalidate = useInvalidateRules()
  return useMutation({
    mutationFn: (data: SaveLogPipelineRuleRequest) => createLogPipelineRule(data),
    onSuccess: invalidate,
  })
}

export function useUpdateLogPipelineRuleMutation() {
  const invalidate = useInvalidateRules()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: SaveLogPipelineRuleRequest }) =>
      updateLogPipelineRule(id, data),
    onSuccess: invalidate,
  })
}

export function useDeleteLogPipelineRuleMutation() {
  const invalidate = useInvalidateRules()
  return useMutation({
    mutationFn: (id: number) => deleteLogPipelineRule(id),
    onSuccess: invalidate,
  })
}

export function useReorderLogPipelineRulesMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => reorderLogPipelineRules(ids),
    onSuccess: (rules) => {
      queryClient.setQueryData(logPipelineRulesQueryKey, rules)
    },
  })
}

export function useTestLogPipelineMutation() {
  return useMutation({
    mutationFn: (data: TestLogPipelineRequest) => testLogPipeline(data),
  })
}
