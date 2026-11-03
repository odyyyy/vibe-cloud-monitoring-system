import { describe, expect, it } from 'vitest'
import type { LogPipelineRule } from '../../api/logPipelines'
import {
  buildActionConfig,
  pipelineActionLabel,
  pipelineRuleFormSchema,
  summarizeAction,
  toPipelineRuleRequest,
  type PipelineRuleFormValues,
} from './schemas'

const baseForm: PipelineRuleFormValues = {
  name: '  drop health checks  ',
  applicationId: '7',
  filter: 'level:DEBUG',
  action: 'DROP',
  pattern: '',
  target: 'MESSAGE',
  field: '',
  key: '',
  value: '',
}

function parse(overrides: Partial<typeof baseForm> = {}) {
  const result = pipelineRuleFormSchema.safeParse({ ...baseForm, ...overrides })
  if (!result.success) {
    throw new Error(result.error.issues[0]?.message)
  }
  return result.data
}

function storedRule(overrides: Partial<LogPipelineRule> = {}): LogPipelineRule {
  return {
    id: 1,
    organizationId: 1,
    applicationId: null,
    name: 'rule',
    filter: '*',
    action: 'DROP',
    actionConfig: {},
    orderIndex: 0,
    extractedFields: [],
    createdAt: '2026-10-09T00:00:00Z',
    updatedAt: '2026-10-09T00:00:00Z',
    ...overrides,
  }
}

describe('pipelineRuleFormSchema', () => {
  it('trims the name and maps the global scope to null', () => {
    const request = toPipelineRuleRequest(parse({ applicationId: 'all' }))

    expect(request.name).toBe('drop health checks')
    expect(request.applicationId).toBeNull()
    expect(request.actionConfig).toEqual({})
  })

  it('maps the selected application to a number', () => {
    expect(toPipelineRuleRequest(parse()).applicationId).toBe(7)
  })

  it('accepts filters up to the backend limit', () => {
    expect(pipelineRuleFormSchema.safeParse({ ...baseForm, filter: 'a'.repeat(1024) }).success).toBe(true)
    expect(pipelineRuleFormSchema.safeParse({ ...baseForm, filter: 'a'.repeat(1025) }).success).toBe(false)
  })

  it('requires a pattern for PARSE_REGEX and MASK', () => {
    expect(pipelineRuleFormSchema.safeParse({ ...baseForm, action: 'PARSE_REGEX' }).success).toBe(false)
    expect(pipelineRuleFormSchema.safeParse({ ...baseForm, action: 'MASK' }).success).toBe(false)
  })

  it('builds the documented action configurations', () => {
    const drop = parse()
    const parseRegex = parse({ action: 'PARSE_REGEX', pattern: 'order (?P<order_id>\\d+)' })
    const add = parse({ action: 'ADD_ATTRIBUTE', key: 'env', value: 'prod' })
    const maskMessage = parse({ action: 'MASK', pattern: '\\d{4}', target: 'MESSAGE' })
    const maskContext = parse({
      action: 'MASK',
      pattern: '\\d{4}',
      target: 'CONTEXT',
      field: 'card_number',
    })

    expect(buildActionConfig(drop)).toEqual({})
    expect(buildActionConfig(parseRegex)).toEqual({ pattern: 'order (?P<order_id>\\d+)' })
    expect(buildActionConfig(add)).toEqual({ key: 'env', value: 'prod' })
    expect(buildActionConfig(maskMessage)).toEqual({ pattern: '\\d{4}', target: 'MESSAGE' })
    expect(buildActionConfig(maskContext)).toEqual({
      pattern: '\\d{4}',
      target: 'CONTEXT',
      field: 'card_number',
    })
  })

  it('requires an attribute key and value', () => {
    expect(pipelineRuleFormSchema.safeParse({ ...baseForm, action: 'ADD_ATTRIBUTE' }).success).toBe(false)
    expect(
      pipelineRuleFormSchema.safeParse({
        ...baseForm,
        action: 'ADD_ATTRIBUTE',
        key: 'bad key',
        value: 'x',
      }).success,
    ).toBe(false)
  })

  it('requires a context field for a context mask', () => {
    expect(
      pipelineRuleFormSchema.safeParse({
        ...baseForm,
        action: 'MASK',
        pattern: 'x',
        target: 'CONTEXT',
      }).success,
    ).toBe(false)
  })
})

describe('summarizeAction', () => {
  it('describes every action', () => {
    expect(summarizeAction(storedRule())).toBe('Drops matching events')
    expect(
      summarizeAction(storedRule({ action: 'PARSE_REGEX', extractedFields: ['order_id', 'user_id'] })),
    ).toBe('Extracts order_id, user_id')
    expect(
      summarizeAction(storedRule({ action: 'ADD_ATTRIBUTE', actionConfig: { key: 'env', value: 'prod' } })),
    ).toBe('env = prod')
    expect(
      summarizeAction(
        storedRule({ action: 'MASK', actionConfig: { target: 'context', field: 'card_number' } }),
      ),
    ).toBe('Masks context.card_number')
    expect(summarizeAction(storedRule({ action: 'MASK', actionConfig: { target: 'MESSAGE' } }))).toBe(
      'Masks the message',
    )
  })

  it('labels actions for the select and table', () => {
    expect(pipelineActionLabel('DROP')).toBe('Drop event')
    expect(pipelineActionLabel('PARSE_REGEX')).toBe('Parse regex')
    expect(pipelineActionLabel('ADD_ATTRIBUTE')).toBe('Add attribute')
    expect(pipelineActionLabel('MASK')).toBe('Mask value')
  })
})
