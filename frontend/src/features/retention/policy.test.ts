import { describe, expect, it } from 'vitest'
import type { RetentionPolicy } from '../../api/retention'
import {
  formFromPolicy,
  isPolicyDirty,
  isPolicyReduction,
  validatePolicyForm,
} from './policy'

const POLICY: RetentionPolicy = {
  organizationId: 1,
  logsRetentionDays: 14,
  metricsRetentionDays: 30,
  tracesRetentionDays: 7,
  updatedAt: null,
  updatedBy: null,
  maxLogsRetentionDays: 90,
  maxMetricsRetentionDays: 365,
  maxTracesRetentionDays: 30,
}

describe('formFromPolicy', () => {
  it('renders the effective values as editable strings', () => {
    expect(formFromPolicy(POLICY)).toEqual({ logs: '14', metrics: '30', traces: '7' })
  })
})

describe('isPolicyDirty', () => {
  it('is false for the unchanged policy', () => {
    expect(isPolicyDirty(formFromPolicy(POLICY), POLICY)).toBe(false)
  })

  it('detects a change in any field', () => {
    expect(isPolicyDirty({ logs: '30', metrics: '30', traces: '7' }, POLICY)).toBe(true)
  })
})

describe('validatePolicyForm', () => {
  it('returns numeric values within bounds', () => {
    expect(validatePolicyForm({ logs: '30', metrics: '60', traces: '14' }, POLICY)).toEqual({
      logsRetentionDays: 30,
      metricsRetentionDays: 60,
      tracesRetentionDays: 14,
    })
  })

  it('rejects zero, negative, fractional and above-limit values', () => {
    expect(() => validatePolicyForm({ logs: '0', metrics: '30', traces: '7' }, POLICY)).toThrow(/between 1 and 90/)
    expect(() => validatePolicyForm({ logs: '14', metrics: '-1', traces: '7' }, POLICY)).toThrow(/between 1 and 365/)
    expect(() => validatePolicyForm({ logs: '14', metrics: '30', traces: '1.5' }, POLICY)).toThrow(/between 1 and 30/)
    expect(() => validatePolicyForm({ logs: '14', metrics: '366', traces: '7' }, POLICY)).toThrow(/between 1 and 365/)
    expect(() => validatePolicyForm({ logs: '', metrics: '30', traces: '7' }, POLICY)).toThrow()
  })
})

describe('isPolicyReduction', () => {
  it('is true when any window becomes shorter', () => {
    expect(isPolicyReduction(
      { logsRetentionDays: 7, metricsRetentionDays: 30, tracesRetentionDays: 7 },
      POLICY,
    )).toBe(true)
  })

  it('is false when every window is unchanged or longer', () => {
    expect(isPolicyReduction(
      { logsRetentionDays: 14, metricsRetentionDays: 30, tracesRetentionDays: 7 },
      POLICY,
    )).toBe(false)
    expect(isPolicyReduction(
      { logsRetentionDays: 30, metricsRetentionDays: 60, tracesRetentionDays: 14 },
      POLICY,
    )).toBe(false)
  })
})
