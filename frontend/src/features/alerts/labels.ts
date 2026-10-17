import type { AlertCondition, AlertRuleType, IncidentStatus } from '../../api/alerts'

const RULE_TYPE_LABELS: Record<AlertRuleType, string> = {
  METRIC_THRESHOLD: 'Metric threshold',
  HEALTH_CHECK: 'Health check',
}

const CONDITION_LABELS: Record<AlertCondition, string> = {
  GREATER_THAN: 'greater than',
  LESS_THAN: 'less than',
  EQUAL: 'equal to',
}

const CONDITION_SYMBOLS: Record<AlertCondition, string> = {
  GREATER_THAN: '>',
  LESS_THAN: '<',
  EQUAL: '=',
}

const STATUS_LABELS: Record<IncidentStatus, string> = {
  FIRING: 'Firing',
  RESOLVED: 'Resolved',
}

export function alertRuleTypeLabel(type: AlertRuleType): string {
  return RULE_TYPE_LABELS[type]
}

export function alertConditionLabel(condition: AlertCondition): string {
  return CONDITION_LABELS[condition]
}

export function alertConditionSymbol(condition: AlertCondition): string {
  return CONDITION_SYMBOLS[condition]
}

export function incidentStatusLabel(status: IncidentStatus): string {
  return STATUS_LABELS[status]
}
