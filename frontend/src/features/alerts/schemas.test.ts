import { describe, expect, it } from 'vitest'
import {
  alertRuleFormSchema,
  isEmailAddress,
  isHttpsUrl,
  isSlackWebhookUrl,
  notificationChannelFormSchema,
} from './schemas'

function condition(overrides: Record<string, unknown> = {}) {
  return { target: 'system.cpu.usage', operator: 'GREATER_THAN', threshold: '90', ...overrides }
}

function validMetricRule(overrides: Record<string, unknown> = {}) {
  return {
    name: 'High CPU',
    type: 'METRIC_THRESHOLD',
    applicationId: 7,
    conditions: [condition()],
    logicOperator: 'AND',
    durationMinutes: '5',
    logLevel: null,
    logQuery: '',
    windowMinutes: '5',
    comparison: 'GREATER_OR_EQUAL',
    countThreshold: '10',
    isActive: true,
    ...overrides,
  }
}

function validHealthRule(overrides: Record<string, unknown> = {}) {
  return {
    name: 'API down',
    type: 'HEALTH_CHECK',
    applicationId: null,
    conditions: [condition({ target: '12', operator: 'EQUAL', threshold: '0' })],
    logicOperator: 'AND',
    durationMinutes: '1',
    logLevel: null,
    logQuery: '',
    windowMinutes: '5',
    comparison: 'GREATER_OR_EQUAL',
    countThreshold: '10',
    isActive: true,
    ...overrides,
  }
}

function validLogRule(overrides: Record<string, unknown> = {}) {
  return {
    name: 'Checkout errors',
    type: 'LOG',
    applicationId: 7,
    conditions: [],
    logicOperator: 'AND',
    durationMinutes: '5',
    logLevel: 'ERROR',
    logQuery: '',
    windowMinutes: '10',
    comparison: 'GREATER_THAN',
    countThreshold: '25',
    isActive: true,
    ...overrides,
  }
}

describe('isHttpsUrl', () => {
  it('accepts absolute https URLs', () => {
    expect(isHttpsUrl('https://hooks.example.com/alerts')).toBe(true)
  })

  it('rejects http and non-http schemes', () => {
    expect(isHttpsUrl('http://hooks.example.com')).toBe(false)
    expect(isHttpsUrl('ftp://hooks.example.com')).toBe(false)
    expect(isHttpsUrl('javascript:alert(1)')).toBe(false)
  })

  it('rejects values that are not absolute URLs', () => {
    expect(isHttpsUrl('hooks.example.com')).toBe(false)
    expect(isHttpsUrl('')).toBe(false)
  })
})

describe('isSlackWebhookUrl', () => {
  it('accepts slack.com hosts over https', () => {
    expect(isSlackWebhookUrl('https://hooks.slack.com/services/T/B/X')).toBe(true)
  })

  it('rejects non-slack hosts and non-https schemes', () => {
    expect(isSlackWebhookUrl('https://evil.example.com/hook')).toBe(false)
    expect(isSlackWebhookUrl('http://hooks.slack.com/services/T/B/X')).toBe(false)
  })
})

describe('isEmailAddress', () => {
  it('accepts simple addresses', () => {
    expect(isEmailAddress('oncall@example.com')).toBe(true)
  })

  it('rejects malformed addresses', () => {
    expect(isEmailAddress('not-an-email')).toBe(false)
    expect(isEmailAddress('a@b')).toBe(false)
    expect(isEmailAddress('a b@example.com')).toBe(false)
  })
})

describe('alertRuleFormSchema', () => {
  it('accepts a valid metric rule and coerces numbers', () => {
    const result = alertRuleFormSchema.safeParse(validMetricRule())
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.conditions[0]?.threshold).toBe(90)
      expect(result.data.durationMinutes).toBe(5)
    }
  })

  it('accepts multiple conditions joined by OR', () => {
    const result = alertRuleFormSchema.safeParse(
      validMetricRule({
        logicOperator: 'OR',
        conditions: [condition(), condition({ target: 'system.memory.usage', threshold: '85' })],
      }),
    )
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.conditions).toHaveLength(2)
      expect(result.data.logicOperator).toBe('OR')
    }
  })

  it('accepts a valid health-check rule', () => {
    expect(alertRuleFormSchema.safeParse(validHealthRule()).success).toBe(true)
  })

  it('trims condition targets', () => {
    const result = alertRuleFormSchema.safeParse(validMetricRule({ conditions: [condition({ target: '  cpu  ' })] }))
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.conditions[0]?.target).toBe('cpu')
    }
  })

  it('rejects an empty condition list', () => {
    expect(alertRuleFormSchema.safeParse(validMetricRule({ conditions: [] })).success).toBe(false)
  })

  it('rejects more than the maximum number of conditions', () => {
    const conditions = Array.from({ length: 11 }, (_, index) => condition({ target: `metric.${index}` }))
    expect(alertRuleFormSchema.safeParse(validMetricRule({ conditions })).success).toBe(false)
  })

  it('rejects a blank name', () => {
    expect(alertRuleFormSchema.safeParse(validMetricRule({ name: '   ' })).success).toBe(false)
  })

  it('rejects a non-numeric threshold', () => {
    expect(alertRuleFormSchema.safeParse(validMetricRule({ conditions: [condition({ threshold: 'abc' })] })).success).toBe(
      false,
    )
  })

  it('rejects durations outside 1-1440 minutes', () => {
    expect(alertRuleFormSchema.safeParse(validMetricRule({ durationMinutes: '0' })).success).toBe(false)
    expect(alertRuleFormSchema.safeParse(validMetricRule({ durationMinutes: '1441' })).success).toBe(false)
    expect(alertRuleFormSchema.safeParse(validMetricRule({ durationMinutes: '2.5' })).success).toBe(false)
  })

  it('requires an application for a metric rule', () => {
    expect(alertRuleFormSchema.safeParse(validMetricRule({ applicationId: null })).success).toBe(false)
  })

  it('requires a numeric target for a health-check rule', () => {
    expect(alertRuleFormSchema.safeParse(validHealthRule({ conditions: [condition({ target: 'nope' })] })).success).toBe(
      false,
    )
  })

  it('rejects multiple conditions for a health-check rule', () => {
    expect(
      alertRuleFormSchema.safeParse(validHealthRule({ conditions: [condition({ target: '1' }), condition({ target: '2' })] }))
        .success,
    ).toBe(false)
  })

  it('accepts a log rule filtered by level and coerces count fields', () => {
    const result = alertRuleFormSchema.safeParse(validLogRule())
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.windowMinutes).toBe(10)
      expect(result.data.countThreshold).toBe(25)
      expect(result.data.logLevel).toBe('ERROR')
    }
  })

  it('accepts a log rule filtered only by a search query', () => {
    expect(alertRuleFormSchema.safeParse(validLogRule({ logLevel: null, logQuery: 'timeout' })).success).toBe(true)
  })

  it('requires a level or a search query for a log rule', () => {
    expect(alertRuleFormSchema.safeParse(validLogRule({ logLevel: null, logQuery: '   ' })).success).toBe(false)
  })

  it('requires an application for a log rule', () => {
    expect(alertRuleFormSchema.safeParse(validLogRule({ applicationId: null })).success).toBe(false)
  })

  it('rejects log windows outside 1-60 minutes', () => {
    expect(alertRuleFormSchema.safeParse(validLogRule({ windowMinutes: '0' })).success).toBe(false)
    expect(alertRuleFormSchema.safeParse(validLogRule({ windowMinutes: '61' })).success).toBe(false)
    expect(alertRuleFormSchema.safeParse(validLogRule({ windowMinutes: '2.5' })).success).toBe(false)
  })

  it('rejects invalid log count thresholds', () => {
    expect(alertRuleFormSchema.safeParse(validLogRule({ countThreshold: '-1' })).success).toBe(false)
    expect(alertRuleFormSchema.safeParse(validLogRule({ countThreshold: '1.5' })).success).toBe(false)
    expect(alertRuleFormSchema.safeParse(validLogRule({ countThreshold: '' })).success).toBe(false)
  })

  it('does not offer equality for log count comparisons', () => {
    expect(alertRuleFormSchema.safeParse(validLogRule({ comparison: 'EQUAL' })).success).toBe(false)
  })
})

describe('notificationChannelFormSchema', () => {
  it('accepts a valid https webhook', () => {
    expect(
      notificationChannelFormSchema.safeParse({ type: 'WEBHOOK', url: 'https://hooks.example.com/alerts', isActive: true })
        .success,
    ).toBe(true)
  })

  it('rejects a non-https webhook URL', () => {
    expect(
      notificationChannelFormSchema.safeParse({ type: 'WEBHOOK', url: 'http://hooks.example.com', isActive: true }).success,
    ).toBe(false)
  })

  it('accepts a slack.com webhook and rejects other hosts', () => {
    expect(
      notificationChannelFormSchema.safeParse({
        type: 'SLACK',
        slackWebhookUrl: 'https://hooks.slack.com/services/T/B/X',
        isActive: true,
      }).success,
    ).toBe(true)
    expect(
      notificationChannelFormSchema.safeParse({
        type: 'SLACK',
        slackWebhookUrl: 'https://evil.example.com/hook',
        isActive: true,
      }).success,
    ).toBe(false)
  })

  it('accepts a valid email and rejects a blank one', () => {
    expect(
      notificationChannelFormSchema.safeParse({ type: 'EMAIL', emailAddress: 'oncall@example.com', isActive: true })
        .success,
    ).toBe(true)
    expect(
      notificationChannelFormSchema.safeParse({ type: 'EMAIL', emailAddress: '   ', isActive: true }).success,
    ).toBe(false)
  })
})
