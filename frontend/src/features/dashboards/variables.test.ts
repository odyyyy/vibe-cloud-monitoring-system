import { describe, expect, it } from 'vitest'
import type { DashboardVariable } from '../../api/dashboards'
import {
  MAX_VARIABLES_PER_DASHBOARD,
  defaultVariableValues,
  interpolate,
  interpolateWidgetConfig,
  isValidVariableName,
  parseOptionsText,
  validateVariables,
} from './variables'

function variable(partial: Partial<DashboardVariable> = {}): DashboardVariable {
  return {
    name: 'env',
    type: 'custom',
    options: ['prod', 'staging'],
    defaultValue: 'prod',
    ...partial,
  }
}

describe('isValidVariableName', () => {
  it('accepts names with letters, digits and underscores', () => {
    expect(isValidVariableName('env')).toBe(true)
    expect(isValidVariableName('host_name')).toBe(true)
    expect(isValidVariableName('env1')).toBe(true)
  })

  it('rejects names with other characters or empty names', () => {
    expect(isValidVariableName('')).toBe(false)
    expect(isValidVariableName('env name')).toBe(false)
    expect(isValidVariableName('env-name')).toBe(false)
    expect(isValidVariableName('env.name')).toBe(false)
    expect(isValidVariableName('env$')).toBe(false)
  })
})

describe('defaultVariableValues', () => {
  it('uses the declared default value', () => {
    expect(defaultVariableValues([variable()])).toEqual({ env: 'prod' })
  })

  it('falls back to the first option when the default is empty', () => {
    expect(defaultVariableValues([variable({ defaultValue: '' })])).toEqual({ env: 'prod' })
  })

  it('handles variables without any options', () => {
    expect(defaultVariableValues([variable({ options: [], defaultValue: '' })])).toEqual({ env: '' })
  })
})

describe('interpolate', () => {
  it('replaces every occurrence of a known placeholder', () => {
    expect(interpolate('${env}-${env}-cpu', { env: 'prod' })).toBe('prod-prod-cpu')
  })

  it('leaves unknown and empty-value placeholders untouched', () => {
    expect(interpolate('${env}/${host}', { env: 'prod' })).toBe('prod/${host}')
    expect(interpolate('${env}', { env: '' })).toBe('${env}')
  })

  it('ignores malformed or invalid placeholders', () => {
    expect(interpolate('$env ${env-x} ${env}', { env: 'prod' })).toBe('$env ${env-x} prod')
    expect(interpolate('${}', {})).toBe('${}')
  })

  it('never evaluates the substituted value', () => {
    expect(interpolate('${cmd}', { cmd: '${other}' })).toBe('${other}')
  })
})

describe('interpolateWidgetConfig', () => {
  it('interpolates the metric name of a chart widget', () => {
    const result = interpolateWidgetConfig(
      {
        type: 'METRIC_CHART',
        config: {
          applicationId: 3,
          metricName: 'cpu.${env}.usage',
          aggregation: 'AVG',
          intervalMinutes: 5,
          timeRangeHours: 1,
        },
      },
      { env: 'prod' },
    )

    expect(result.type).toBe('METRIC_CHART')
    if (result.type === 'METRIC_CHART') {
      expect(result.config.metricName).toBe('cpu.prod.usage')
      expect(result.config.aggregation).toBe('AVG')
    }
  })

  it('interpolates the search text of a log table widget', () => {
    const result = interpolateWidgetConfig(
      { type: 'LOG_TABLE', config: { applicationId: 3, searchText: 'level=${level}', timeRangeHours: 1 } },
      { level: 'error' },
    )

    expect(result.type).toBe('LOG_TABLE')
    if (result.type === 'LOG_TABLE') {
      expect(result.config.searchText).toBe('level=error')
    }
  })

  it('leaves an absent search text absent', () => {
    const result = interpolateWidgetConfig(
      { type: 'LOG_TABLE', config: { applicationId: 3, timeRangeHours: 1 } },
      { level: 'error' },
    )

    expect(result.type === 'LOG_TABLE' && result.config.searchText).toBeUndefined()
  })

  it('passes status widgets through unchanged', () => {
    const status = { type: 'STATUS' as const, config: { applicationId: 3, timeRangeHours: 1 } }
    expect(interpolateWidgetConfig(status, { env: 'prod' })).toBe(status)
  })
})

describe('parseOptionsText', () => {
  it('splits, trims and drops empty options', () => {
    expect(parseOptionsText('prod, staging ,,dev')).toEqual(['prod', 'staging', 'dev'])
  })

  it('removes duplicates while keeping order', () => {
    expect(parseOptionsText('prod,prod,staging')).toEqual(['prod', 'staging'])
  })

  it('returns an empty list for blank input', () => {
    expect(parseOptionsText('   ')).toEqual([])
  })
})

describe('validateVariables', () => {
  it('accepts consistent definitions', () => {
    expect(validateVariables([variable()])).toBeNull()
    expect(validateVariables([])).toBeNull()
  })

  it('rejects invalid names, duplicates and empty option lists', () => {
    expect(validateVariables([variable({ name: 'env name' })])).toContain('only letters')
    expect(validateVariables([variable(), variable()])).toContain('Duplicate')
    expect(validateVariables([variable({ options: [], defaultValue: '' })])).toContain('at least one option')
  })

  it('rejects a default value that is not among the options', () => {
    expect(validateVariables([variable({ defaultValue: 'dev' })])).toContain('must be one of its options')
  })

  it('allows an empty default value', () => {
    expect(validateVariables([variable({ defaultValue: '' })])).toBeNull()
  })

  it('rejects too many variables', () => {
    const many = Array.from({ length: MAX_VARIABLES_PER_DASHBOARD + 1 }, (_, index) =>
      variable({ name: `var_${index}` }),
    )
    expect(validateVariables(many)).toContain(String(MAX_VARIABLES_PER_DASHBOARD))
  })
})
