import type {
  AlertCondition,
  AlertRule,
  AlertRuleType,
  IncidentStatus,
  LogicOperator,
  NotificationChannelType,
  NotificationDeliveryStatus,
  NotificationEventType,
} from '../../api/alerts'

const RULE_TYPE_LABELS: Record<AlertRuleType, string> = {
  METRIC_THRESHOLD: 'Metric',
  HEALTH_CHECK: 'Health check',
  LOG: 'Log',
}

const CONDITION_LABELS: Record<AlertCondition, string> = {
  GREATER_THAN: 'greater than',
  GREATER_OR_EQUAL: 'greater than or equal',
  LESS_THAN: 'less than',
  EQUAL: 'equal to',
}

const CONDITION_SYMBOLS: Record<AlertCondition, string> = {
  GREATER_THAN: '>',
  GREATER_OR_EQUAL: '>=',
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
  PAGERDUTY: 'PagerDuty',
  TEAMS: 'Microsoft Teams',
}

const DELIVERY_STATUS_LABELS: Record<NotificationDeliveryStatus, string> = {
  PENDING: 'Pending',
  SUCCESS: 'Delivered',
  FAILED: 'Failed',
}

const EVENT_TYPE_LABELS: Record<NotificationEventType, string> = {
  INCIDENT_OPENED: 'Incident opened',
  INCIDENT_ACKNOWLEDGED: 'Incident acknowledged',
  INCIDENT_RESOLVED: 'Incident resolved',
  SCHEDULED_REPORT: 'Scheduled report',
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

export function notificationDeliveryStatusLabel(status: NotificationDeliveryStatus): string {
  return DELIVERY_STATUS_LABELS[status]
}

export function notificationEventTypeLabel(type: NotificationEventType): string {
  return EVENT_TYPE_LABELS[type]
}

/** Human-readable log-rule summary, for example: `ERROR logs containing "timeout" >= 10 in 5 min`. */
export function logRuleSummary(rule: AlertRule): string {
  const subject = rule.logLevel ? `${rule.logLevel} logs` : 'logs'
  const query = rule.logQuery ? ` containing "${rule.logQuery}"` : ''
  const comparison = rule.comparison ? alertConditionSymbol(rule.comparison) : '>='
  const threshold = rule.countThreshold ?? 0
  const window = rule.windowMinutes ?? 0
  return `${subject}${query} ${comparison} ${threshold} in ${window} min`
}
