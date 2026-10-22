import { describe, expect, it } from 'vitest'
import type { AlertRule } from '../../api/alerts'
import { logRuleSummary } from './labels'

function logRule(overrides: Partial<AlertRule> = {}): AlertRule {
  return {
    id: 1,
    organizationId: 1,
    applicationId: 7,
    name: 'Checkout timeouts',
    type: 'LOG',
    target: 'logs',
    condition: 'GREATER_OR_EQUAL',
    threshold: 10,
    conditions: [],
    logicOperator: 'AND',
    durationMinutes: 1,
    logLevel: 'ERROR',
    logQuery: 'timeout',
    windowMinutes: 5,
    comparison: 'GREATER_OR_EQUAL',
    countThreshold: 10,
    isActive: true,
    createdAt: '2026-10-17T10:00:00Z',
    updatedAt: '2026-10-17T10:00:00Z',
    ...overrides,
  }
}

describe('logRuleSummary', () => {
  it('describes level, query, comparison, threshold and window', () => {
    expect(logRuleSummary(logRule())).toBe(
      'ERROR logs containing "timeout" >= 10 in 5 min',
    )
  })

  it('describes a level-only rule', () => {
    expect(
      logRuleSummary(logRule({ logQuery: null, comparison: 'LESS_THAN', countThreshold: 3, windowMinutes: 1 })),
    ).toBe('ERROR logs < 3 in 1 min')
  })

  it('describes a query-only rule with fallbacks', () => {
    expect(
      logRuleSummary(
        logRule({
          logLevel: null,
          logQuery: 'database',
          comparison: null,
          countThreshold: null,
          windowMinutes: null,
        }),
      ),
    ).toBe('logs containing "database" >= 0 in 0 min')
  })

  it('describes a rule without filters', () => {
    expect(
      logRuleSummary(
        logRule({
          logLevel: null,
          logQuery: null,
          comparison: null,
          countThreshold: null,
          windowMinutes: null,
        }),
      ),
    ).toBe('logs >= 0 in 0 min')
  })
})
