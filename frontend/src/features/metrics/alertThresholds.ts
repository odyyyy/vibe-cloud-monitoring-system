import type { AlertCondition, AlertRule } from '../../api/alerts'

/**
 * Pure helpers for the alert threshold overlay on {@link MetricChart}.
 *
 * A single metric can be watched by several rules (for example a warning at 80% and a critical
 * at 95%), and a compound rule may reference metrics other than the one being charted. This
 * module reduces the raw rules into the horizontal reference lines that apply to one metric and
 * computes a Y-axis domain wide enough to keep every line visible.
 */

export interface AlertThresholdLine {
  /** Stable React key. */
  key: string
  ruleId: number
  ruleName: string
  value: number
  operator: AlertCondition
  /** Short condition label such as "> 90". */
  label: string
  color: string
}

const CONDITION_SYMBOLS: Record<AlertCondition, string> = {
  GREATER_THAN: '>',
  LESS_THAN: '<',
  EQUAL: '=',
}

/**
 * Ordered palette: the highest threshold is treated as the most severe (red) and lower ones as
 * warnings (amber/yellow), which matches the common warning/critical convention.
 */
const THRESHOLD_COLORS = ['#ef4444', '#f59e0b', '#eab308', '#f97316']

function formatValue(value: number): string {
  return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(4)))
}

function conditionLabel(operator: AlertCondition, value: number): string {
  return `${CONDITION_SYMBOLS[operator] ?? operator} ${formatValue(value)}`
}

/**
 * Returns the threshold lines that apply to `metricName`, sorted so the highest threshold is
 * drawn and colored first. Rules that do not reference the metric produce no line, so a chart
 * with no configured alerts simply renders normally.
 */
export function buildAlertThresholdLines(
  rules: readonly AlertRule[],
  metricName: string | null | undefined,
): AlertThresholdLine[] {
  if (!metricName) {
    return []
  }

  const lines: AlertThresholdLine[] = []
  for (const rule of rules) {
    if (rule.type !== 'METRIC_THRESHOLD') {
      continue
    }
    const conditions =
      rule.conditions.length > 0
        ? rule.conditions
        : [{ target: rule.target, operator: rule.condition, threshold: rule.threshold }]

    conditions.forEach((condition, index) => {
      if (condition.target !== metricName || !Number.isFinite(condition.threshold)) {
        return
      }
      lines.push({
        key: `${rule.id}-${index}`,
        ruleId: rule.id,
        ruleName: rule.name,
        value: condition.threshold,
        operator: condition.operator,
        label: conditionLabel(condition.operator, condition.threshold),
        color: '',
      })
    })
  }

  lines.sort((a, b) => b.value - a.value)
  return lines.map((line, index) => ({
    ...line,
    color: THRESHOLD_COLORS[index % THRESHOLD_COLORS.length],
  }))
}

/**
 * Y-axis domain that contains every data point and every visible threshold, with a little
 * headroom so a threshold label at the top is not clipped. Returns `undefined` when there is
 * nothing to constrain, letting Recharts compute its default domain.
 */
export function computeThresholdYAxisDomain(
  values: readonly number[],
  thresholds: readonly number[],
): [number, number] | undefined {
  const finiteValues = values.filter(Number.isFinite)
  const finiteThresholds = thresholds.filter(Number.isFinite)
  if (finiteThresholds.length === 0 || finiteValues.length === 0) {
    return undefined
  }

  const min = Math.min(0, ...finiteValues, ...finiteThresholds)
  const max = Math.max(...finiteValues, ...finiteThresholds)
  const pad = (max - min) * 0.05 || 1
  return [min, max + pad]
}
