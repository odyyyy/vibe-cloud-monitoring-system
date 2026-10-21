import { describe, expect, it } from 'vitest'
import type { AlertCondition, AlertRule } from '../../api/alerts'
import { buildAlertThresholdLines, computeThresholdYAxisDomain } from './alertThresholds'

function rule(overrides: Partial<AlertRule> = {}): AlertRule {
  return {
    id: 1,
    organizationId: 1,
    applicationId: 7,
    name: 'High CPU',
    type: 'METRIC_THRESHOLD',
    target: 'system.cpu.usage',
    condition: 'GREATER_THAN',
    threshold: 90,
    conditions: [],
    logicOperator: 'AND',
    durationMinutes: 5,
    isActive: true,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    ...overrides,
  }
}

function condition(target: string, operator: AlertCondition, threshold: number) {
  return { target, operator, threshold }
}

describe('buildAlertThresholdLines', () => {
  it('returns nothing when no metric name is selected', () => {
    expect(buildAlertThresholdLines([rule()], null)).toEqual([])
    expect(buildAlertThresholdLines([rule()], '')).toEqual([])
  })

  it('builds a labelled line for a matching legacy rule', () => {
    const lines = buildAlertThresholdLines([rule()], 'system.cpu.usage')

    expect(lines).toHaveLength(1)
    expect(lines[0].value).toBe(90)
    expect(lines[0].label).toBe('> 90')
    expect(lines[0].ruleName).toBe('High CPU')
  })

  it('ignores rules that do not reference the metric', () => {
    expect(buildAlertThresholdLines([rule({ target: 'system.memory.usage' })], 'system.cpu.usage')).toEqual([])
  })

  it('reads compound conditions, not only the legacy target', () => {
    const compound = rule({
      target: 'system.cpu.usage',
      conditions: [
        condition('system.cpu.usage', 'GREATER_THAN', 90),
        condition('system.memory.usage', 'GREATER_THAN', 85),
      ],
    })

    const lines = buildAlertThresholdLines([compound], 'system.memory.usage')

    expect(lines).toHaveLength(1)
    expect(lines[0].value).toBe(85)
  })

  it('skips health-check rules', () => {
    expect(buildAlertThresholdLines([rule({ type: 'HEALTH_CHECK' })], 'system.cpu.usage')).toEqual([])
  })

  it('sorts the highest threshold first and gives each a distinct color', () => {
    const warning = rule({ id: 1, name: 'Warn', threshold: 80 })
    const critical = rule({ id: 2, name: 'Critical', threshold: 95 })

    const lines = buildAlertThresholdLines([warning, critical], 'system.cpu.usage')

    expect(lines.map((line) => line.value)).toEqual([95, 80])
    expect(lines[0].color).not.toBe(lines[1].color)
  })
})

describe('computeThresholdYAxisDomain', () => {
  it('returns undefined without thresholds so Recharts keeps its default domain', () => {
    expect(computeThresholdYAxisDomain([10, 20, 30], [])).toBeUndefined()
  })

  it('expands above the data maximum so the threshold line stays visible', () => {
    const domain = computeThresholdYAxisDomain([10, 20, 30], [90])

    expect(domain).toBeDefined()
    expect(domain![0]).toBe(0)
    expect(domain![1]).toBeGreaterThan(90)
  })

  it('expands below the data minimum for a low threshold', () => {
    const domain = computeThresholdYAxisDomain([100, 200], [-5])

    expect(domain![0]).toBeLessThanOrEqual(-5)
  })
})
