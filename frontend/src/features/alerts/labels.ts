import type { AlertCondition, AlertRuleType, IncidentStatus, LogicOperator, NotificationChannelType } from '../../api/alerts'

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
  ACKNOWLEDGED: 'Acknowledged',
  RESOLVED: 'Resolved',
}

const LOGIC_OPERATOR_LABELS: Record<LogicOperator, string> = {
  AND: 'AND',
  OR: 'OR',
}

const CHANNEL_TYPE_LABELS: Record<NotificationChannelType, string> = {
  WEBHOOK: 'Webhook',
  SLACK: 'Slack',
  EMAIL: 'Email',
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

export function logicOperatorLabel(operator: LogicOperator): string {
  return LOGIC_OPERATOR_LABELS[operator]
}

export function notificationChannelTypeLabel(type: NotificationChannelType): string {
  return CHANNEL_TYPE_LABELS[type]
}
