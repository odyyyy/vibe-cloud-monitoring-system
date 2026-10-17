import { describe, expect, it } from 'vitest'
import { alertRuleFormSchema, isHttpsUrl, notificationChannelFormSchema } from './schemas'

function validMetricRule(overrides: Record<string, unknown> = {}) {
  return {
    name: 'High CPU',
    type: 'METRIC_THRESHOLD',
    applicationId: 7,
    target: 'system.cpu.usage',
    condition: 'GREATER_THAN',
    threshold: '90',
    durationMinutes: '5',
    isActive: true,
    ...overrides,
  }
}

function validHealthRule(overrides: Record<string, unknown> = {}) {
  return {
    name: 'API down',
    type: 'HEALTH_CHECK',
    applicationId: null,
    target: '12',
    condition: 'EQUAL',
    threshold: '0',
    durationMinutes: '1',
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

describe('alertRuleFormSchema', () => {
  it('accepts a valid metric rule and coerces numbers', () => {
    const result = alertRuleFormSchema.safeParse(validMetricRule())
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.threshold).toBe(90)
      expect(result.data.durationMinutes).toBe(5)
    }
  })

  it('accepts a valid health-check rule', () => {
    expect(alertRuleFormSchema.safeParse(validHealthRule()).success).toBe(true)
  })

  it('trims the name and target', () => {
    const result = alertRuleFormSchema.safeParse(validMetricRule({ name: '  CPU  ', target: '  cpu  ' }))
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.name).toBe('CPU')
      expect(result.data.target).toBe('cpu')
    }
  })

  it('rejects a blank name', () => {
    expect(alertRuleFormSchema.safeParse(validMetricRule({ name: '   ' })).success).toBe(false)
  })

  it('rejects an empty or non-numeric threshold', () => {
    expect(alertRuleFormSchema.safeParse(validMetricRule({ threshold: '' })).success).toBe(false)
    expect(alertRuleFormSchema.safeParse(validMetricRule({ threshold: 'abc' })).success).toBe(false)
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
    expect(alertRuleFormSchema.safeParse(validHealthRule({ target: 'not-a-number' })).success).toBe(false)
  })

  it('rejects names longer than 100 characters', () => {
    expect(alertRuleFormSchema.safeParse(validMetricRule({ name: 'a'.repeat(101) })).success).toBe(false)
  })
})

describe('notificationChannelFormSchema', () => {
  function validChannel(overrides: Record<string, unknown> = {}) {
    return { type: 'WEBHOOK', url: 'https://hooks.example.com/alerts', isActive: true, ...overrides }
  }

  it('accepts a valid https webhook', () => {
    expect(notificationChannelFormSchema.safeParse(validChannel()).success).toBe(true)
  })

  it('rejects a non-https webhook URL', () => {
    expect(notificationChannelFormSchema.safeParse(validChannel({ url: 'http://hooks.example.com' })).success).toBe(false)
  })

  it('rejects a blank URL', () => {
    expect(notificationChannelFormSchema.safeParse(validChannel({ url: '   ' })).success).toBe(false)
  })
})
