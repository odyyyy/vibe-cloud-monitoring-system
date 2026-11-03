import { z } from 'zod'
import type { LogPipelineAction, LogPipelineRule, SaveLogPipelineRuleRequest } from '../../api/logPipelines'

export const MAX_PIPELINE_NAME_LENGTH = 100
export const MAX_PIPELINE_FILTER_LENGTH = 1024
export const MAX_PIPELINE_PATTERN_LENGTH = 512
export const MAX_PIPELINE_ATTRIBUTE_VALUE_LENGTH = 256
export const CONTEXT_KEY_PATTERN = /^[A-Za-z0-9_.-]{1,64}$/

const optionalText = z.string().trim().max(MAX_PIPELINE_ATTRIBUTE_VALUE_LENGTH).optional()
const optionalKey = z.string().trim().max(64).optional()

export const pipelineRuleFormSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, 'Name is required')
      .max(MAX_PIPELINE_NAME_LENGTH, `Name must be at most ${MAX_PIPELINE_NAME_LENGTH} characters`),
    applicationId: z.string().refine((value) => value === 'all' || /^[1-9][0-9]*$/.test(value), {
      message: 'Select an application',
    }),
    filter: z
      .string()
      .trim()
      .min(1, 'Filter is required')
      .max(MAX_PIPELINE_FILTER_LENGTH, `Filter must be at most ${MAX_PIPELINE_FILTER_LENGTH} characters`),
    action: z.enum(['DROP', 'PARSE_REGEX', 'ADD_ATTRIBUTE', 'MASK']),
    pattern: z
      .string()
      .trim()
      .max(MAX_PIPELINE_PATTERN_LENGTH, `Pattern must be at most ${MAX_PIPELINE_PATTERN_LENGTH} characters`)
      .optional(),
    target: z.enum(['MESSAGE', 'CONTEXT']).optional(),
    field: optionalKey,
    key: optionalKey,
    value: optionalText,
  })
  .superRefine((values, context) => {
    if (values.action === 'PARSE_REGEX' || values.action === 'MASK') {
      if (!values.pattern?.trim()) {
        context.addIssue({ code: z.ZodIssueCode.custom, path: ['pattern'], message: 'Pattern is required' })
      } else if (values.pattern.length > MAX_PIPELINE_PATTERN_LENGTH) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['pattern'],
          message: `Pattern must be at most ${MAX_PIPELINE_PATTERN_LENGTH} characters`,
        })
      }
    }

    if (values.action === 'ADD_ATTRIBUTE') {
      if (!values.key || !CONTEXT_KEY_PATTERN.test(values.key)) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['key'],
          message: 'Use letters, digits, dots, dashes and underscores (max 64)',
        })
      }
      if (!values.value) {
        context.addIssue({ code: z.ZodIssueCode.custom, path: ['value'], message: 'Value is required' })
      }
    }

    if (values.action === 'MASK' && values.target === 'CONTEXT') {
      if (!values.field || !CONTEXT_KEY_PATTERN.test(values.field)) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['field'],
          message: 'Use a valid context field name',
        })
      }
    }
  })

export type PipelineRuleFormValues = z.infer<typeof pipelineRuleFormSchema>

export function buildActionConfig(values: PipelineRuleFormValues): Record<string, unknown> {
  switch (values.action) {
    case 'DROP':
      return {}
    case 'PARSE_REGEX':
      return { pattern: values.pattern }
    case 'ADD_ATTRIBUTE':
      return { key: values.key, value: values.value }
    case 'MASK':
      return values.target === 'CONTEXT'
        ? { pattern: values.pattern, target: 'CONTEXT', field: values.field }
        : { pattern: values.pattern, target: 'MESSAGE' }
  }
}

export function toPipelineRuleRequest(values: PipelineRuleFormValues): SaveLogPipelineRuleRequest {
  return {
    name: values.name,
    applicationId: values.applicationId === 'all' ? null : Number(values.applicationId),
    filter: values.filter,
    action: values.action,
    actionConfig: buildActionConfig(values),
  }
}

export function pipelineActionLabel(action: LogPipelineAction): string {
  switch (action) {
    case 'DROP':
      return 'Drop event'
    case 'PARSE_REGEX':
      return 'Parse regex'
    case 'ADD_ATTRIBUTE':
      return 'Add attribute'
    case 'MASK':
      return 'Mask value'
  }
}

export function summarizeAction(rule: LogPipelineRule): string {
  const config = rule.actionConfig ?? {}
  switch (rule.action) {
    case 'DROP':
      return 'Drops matching events'
    case 'PARSE_REGEX':
      return `Extracts ${rule.extractedFields.join(', ') || 'no fields'}`
    case 'ADD_ATTRIBUTE':
      return `${String(config.key ?? '')} = ${String(config.value ?? '')}`
    case 'MASK': {
      const target = String(config.target ?? 'message').toLowerCase()
      return target === 'context' ? `Masks context.${String(config.field ?? '')}` : 'Masks the message'
    }
  }
}
