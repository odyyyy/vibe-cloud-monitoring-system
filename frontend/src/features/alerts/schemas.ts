import { z } from 'zod'

export const ALERT_RULE_TYPES = ['METRIC_THRESHOLD', 'HEALTH_CHECK', 'LOG'] as const
export type AlertRuleTypeValue = (typeof ALERT_RULE_TYPES)[number]

export const ALERT_CONDITIONS = ['GREATER_THAN', 'GREATER_OR_EQUAL', 'LESS_THAN', 'EQUAL'] as const
export type AlertConditionValue = (typeof ALERT_CONDITIONS)[number]

export const LOGIC_OPERATORS = ['AND', 'OR'] as const
export const LOG_LEVELS = ['DEBUG', 'INFO', 'WARN', 'ERROR'] as const
export type LogLevelValue = (typeof LOG_LEVELS)[number]
export const LOG_ALERT_CONDITIONS = ['GREATER_THAN', 'GREATER_OR_EQUAL', 'LESS_THAN'] as const
export type LogAlertConditionValue = (typeof LOG_ALERT_CONDITIONS)[number]
export type LogicOperatorValue = (typeof LOGIC_OPERATORS)[number]

export const NOTIFICATION_CHANNEL_TYPES = ['WEBHOOK', 'SLACK', 'EMAIL', 'PAGERDUTY', 'TEAMS'] as const
export type NotificationChannelTypeValue = (typeof NOTIFICATION_CHANNEL_TYPES)[number]

export const NAME_MAX_LENGTH = 100
export const TARGET_MAX_LENGTH = 255
export const URL_MAX_LENGTH = 2048
export const EMAIL_MAX_LENGTH = 320
export const WEBHOOK_SECRET_MAX_LENGTH = 255
export const MAX_WEBHOOK_HEADERS = 5
export const MAX_HEADER_NAME_LENGTH = 128
export const MAX_HEADER_VALUE_LENGTH = 1024
/** PagerDuty Events API v2 integration keys are alphanumeric. */
export const PAGERDUTY_ROUTING_KEY_PATTERN = /^[A-Za-z0-9]{16,255}$/
export const PAGERDUTY_ROUTING_KEY_MAX_LENGTH = 255
export const MAX_CONDITIONS = 10
export const MIN_DURATION_MINUTES = 1
export const MAX_DURATION_MINUTES = 1440
export const DEFAULT_DURATION_MINUTES = 5
export const DEFAULT_THRESHOLD = 90
export const MIN_WINDOW_MINUTES = 1
export const MAX_WINDOW_MINUTES = 60
export const DEFAULT_WINDOW_MINUTES = 5
export const DEFAULT_COUNT_THRESHOLD = 10
export const DEFAULT_LOG_COMPARISON: LogAlertConditionValue = 'GREATER_OR_EQUAL'
export const LOG_QUERY_MAX_LENGTH = 200
export const DEFAULT_RULE_TYPE: AlertRuleTypeValue = 'METRIC_THRESHOLD'
export const DEFAULT_CONDITION: AlertConditionValue = 'GREATER_THAN'
export const DEFAULT_LOGIC_OPERATOR: LogicOperatorValue = 'AND'

/**
 * Frontend URL validation is a UX aid, not a security boundary. It only checks that the URL is
 * absolute, uses https and has a host; the backend enforces full SSRF protection.
 */
export function isHttpsUrl(value: string): boolean {
  let url: URL
  try {
    url = new URL(value)
  } catch {
    return false
  }
  return url.protocol === 'https:' && url.hostname.length > 0
}

/** Slack incoming webhooks live under slack.com; the backend re-validates the host. */
export function isSlackWebhookUrl(value: string): boolean {
  let url: URL
  try {
    url = new URL(value)
  } catch {
    return false
  }
  if (url.protocol !== 'https:') {
    return false
  }
  const host = url.hostname.toLowerCase()
  return host === 'slack.com' || host.endsWith('.slack.com')
}

/** Basic email format check mirroring the backend's validation. */
export function isEmailAddress(value: string): boolean {
  return value.length <= EMAIL_MAX_LENGTH && /^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$/.test(value)
}

const nameSchema = z
  .string()
  .trim()
  .min(1, 'Name is required')
  .max(NAME_MAX_LENGTH, `Name must be at most ${NAME_MAX_LENGTH} characters`)

const targetSchema = z
  .string()
  .trim()
  .min(1, 'Target is required')
  .max(TARGET_MAX_LENGTH, `Target must be at most ${TARGET_MAX_LENGTH} characters`)

/** Coerces a form string to a finite number, rejecting blanks and non-numeric values. */
function requiredNumberSchema(requiredMessage: string, invalidMessage: string) {
  return z.preprocess(
    (value) => (typeof value === 'string' && value.trim() === '' ? undefined : value),
    z.coerce.number({ invalid_type_error: requiredMessage, required_error: requiredMessage }).finite(invalidMessage),
  )
}

const thresholdSchema = requiredNumberSchema('Threshold is required', 'Threshold must be a number')

const durationMinutesSchema = requiredNumberSchema('Duration is required', 'Duration must be a number')
  .pipe(
    z
      .number()
      .int('Duration must be a whole number')
      .min(MIN_DURATION_MINUTES, `Duration must be between ${MIN_DURATION_MINUTES} and ${MAX_DURATION_MINUTES} minutes`)
      .max(MAX_DURATION_MINUTES, `Duration must be between ${MIN_DURATION_MINUTES} and ${MAX_DURATION_MINUTES} minutes`),
  )

const windowMinutesSchema = requiredNumberSchema('Window is required', 'Window must be a number')
  .pipe(
    z
      .number()
      .int('Window must be a whole number')
      .min(MIN_WINDOW_MINUTES, `Window must be between ${MIN_WINDOW_MINUTES} and ${MAX_WINDOW_MINUTES} minutes`)
      .max(MAX_WINDOW_MINUTES, `Window must be between ${MIN_WINDOW_MINUTES} and ${MAX_WINDOW_MINUTES} minutes`),
  )

const countThresholdSchema = requiredNumberSchema('Count threshold is required', 'Count threshold must be a number')
  .pipe(
    z
      .number()
      .int('Count threshold must be a whole number')
      .min(0, 'Count threshold must be zero or greater'),
  )

export const alertConditionSchema = z.object({
  target: targetSchema,
  operator: z.enum(ALERT_CONDITIONS),
  threshold: thresholdSchema,
})

export const alertRuleFormSchema = z
  .object({
    name: nameSchema,
    type: z.enum(ALERT_RULE_TYPES),
    applicationId: z.number().int().positive().nullable(),
    conditions: z
      .array(alertConditionSchema)
      .max(MAX_CONDITIONS, `At most ${MAX_CONDITIONS} conditions are allowed`),
    logicOperator: z.enum(LOGIC_OPERATORS),
    durationMinutes: durationMinutesSchema,
    logLevel: z.enum(LOG_LEVELS).nullable(),
    logQuery: z
      .string()
      .trim()
      .max(LOG_QUERY_MAX_LENGTH, `Log query must be at most ${LOG_QUERY_MAX_LENGTH} characters`),
    windowMinutes: windowMinutesSchema,
    comparison: z.enum(LOG_ALERT_CONDITIONS),
    countThreshold: countThresholdSchema,
    isActive: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (values.type !== 'LOG' && values.conditions.length === 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['conditions'],
        message: 'Add at least one condition',
      })
    }
    if (values.type === 'METRIC_THRESHOLD' && values.applicationId === null) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['applicationId'],
        message: 'Select an application for a metric rule',
      })
    }
    if (values.type === 'HEALTH_CHECK') {
      if (values.conditions.length !== 1) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['conditions'],
          message: 'A health-check rule uses a single condition',
        })
      } else if (!/^\d+$/.test(values.conditions[0]!.target)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['conditions', 0, 'target'],
          message: 'Select a health check',
        })
      }
    }
    if (values.type === 'LOG') {
      if (values.applicationId === null) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['applicationId'],
          message: 'Select an application for a log rule',
        })
      }
      if (values.logLevel === null && values.logQuery.length === 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['logQuery'],
          message: 'Choose a log level, enter a search query, or both',
        })
      }
    }
  })

export type AlertRuleFormValues = z.infer<typeof alertRuleFormSchema>

/** HTTP header names follow the token grammar and must not be platform-managed. */
export function isWebhookHeaderName(value: string): boolean {
  if (value.length === 0 || value.length > MAX_HEADER_NAME_LENGTH) {
    return false
  }
  if (!/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(value)) {
    return false
  }
  return !['host', 'content-length', 'connection', 'transfer-encoding', 'upgrade', 'x-signature-256', 'x-timestamp']
    .includes(value.toLowerCase())
}

const webhookHeadersSchema = z
  .record(z.string())
  .superRefine((headers, ctx) => {
    const entries = Object.entries(headers)
    if (entries.length > MAX_WEBHOOK_HEADERS) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `At most ${MAX_WEBHOOK_HEADERS} static headers are allowed`,
      })
    }
    for (const [name, value] of entries) {
      if (!isWebhookHeaderName(name.trim())) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: `Header name "${name}" is not valid or is managed by the platform`,
        })
      }
      if (value.length > MAX_HEADER_VALUE_LENGTH) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: `Header values must be at most ${MAX_HEADER_VALUE_LENGTH} characters`,
        })
      }
      if (/[\r\n]/.test(value)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Header values must not contain line breaks',
        })
      }
    }
  })

const webhookChannelSchema = z.object({
  type: z.literal('WEBHOOK'),
  url: z
    .string()
    .trim()
    .min(1, 'Webhook URL is required')
    .max(URL_MAX_LENGTH, `Webhook URL must be at most ${URL_MAX_LENGTH} characters`)
    .refine(isHttpsUrl, 'Enter an absolute https:// URL'),
  secret: z
    .string()
    .trim()
    .max(WEBHOOK_SECRET_MAX_LENGTH, `Secret must be at most ${WEBHOOK_SECRET_MAX_LENGTH} characters`)
    .optional(),
  headers: webhookHeadersSchema,
  isActive: z.boolean(),
})

const pagerDutyChannelSchema = z.object({
  type: z.literal('PAGERDUTY'),
  routingKey: z
    .string()
    .trim()
    .min(1, 'PagerDuty routing key is required')
    .max(PAGERDUTY_ROUTING_KEY_MAX_LENGTH, 'PagerDuty routing key is too long')
    .refine(
      (value) => PAGERDUTY_ROUTING_KEY_PATTERN.test(value),
      'Enter a valid PagerDuty integration key (letters and digits)',
    ),
  isActive: z.boolean(),
})

const teamsChannelSchema = z.object({
  type: z.literal('TEAMS'),
  teamsWebhookUrl: z
    .string()
    .trim()
    .min(1, 'Microsoft Teams webhook URL is required')
    .max(URL_MAX_LENGTH, `Microsoft Teams webhook URL must be at most ${URL_MAX_LENGTH} characters`)
    .refine(isHttpsUrl, 'Enter an absolute https:// URL'),
  isActive: z.boolean(),
})

const slackChannelSchema = z.object({
  type: z.literal('SLACK'),
  slackWebhookUrl: z
    .string()
    .trim()
    .min(1, 'Slack webhook URL is required')
    .max(URL_MAX_LENGTH, `Slack webhook URL must be at most ${URL_MAX_LENGTH} characters`)
    .refine(isSlackWebhookUrl, 'Enter an https://hooks.slack.com URL'),
  isActive: z.boolean(),
})

const emailChannelSchema = z.object({
  type: z.literal('EMAIL'),
  emailAddress: z
    .string()
    .trim()
    .min(1, 'Email address is required')
    .max(EMAIL_MAX_LENGTH, `Email address must be at most ${EMAIL_MAX_LENGTH} characters`)
    .refine(isEmailAddress, 'Enter a valid email address'),
  isActive: z.boolean(),
})

export const notificationChannelFormSchema = z.discriminatedUnion('type', [
  webhookChannelSchema,
  slackChannelSchema,
  emailChannelSchema,
  pagerDutyChannelSchema,
  teamsChannelSchema,
])

export type NotificationChannelFormValues = z.infer<typeof notificationChannelFormSchema>

/**
 * Partial update: an omitted or blank destination keeps the stored value (the API only returns
 * masked URLs, so the edit form cannot pre-fill them). Optional signing material follows the same
 * rule and is only sent when the user actually enters something new.
 */
export const updateNotificationChannelFormSchema = z.object({
  type: z.enum(NOTIFICATION_CHANNEL_TYPES),
  url: z
    .string()
    .trim()
    .max(URL_MAX_LENGTH, `Webhook URL must be at most ${URL_MAX_LENGTH} characters`)
    .refine((value) => value === '' || isHttpsUrl(value), 'Enter an absolute https:// URL')
    .optional(),
  slackWebhookUrl: z
    .string()
    .trim()
    .max(URL_MAX_LENGTH, `Slack webhook URL must be at most ${URL_MAX_LENGTH} characters`)
    .refine((value) => value === '' || isSlackWebhookUrl(value), 'Enter an https://hooks.slack.com URL')
    .optional(),
  emailAddress: z
    .string()
    .trim()
    .max(EMAIL_MAX_LENGTH, `Email address must be at most ${EMAIL_MAX_LENGTH} characters`)
    .refine((value) => value === '' || isEmailAddress(value), 'Enter a valid email address')
    .optional(),
  teamsWebhookUrl: z
    .string()
    .trim()
    .max(URL_MAX_LENGTH, `Microsoft Teams webhook URL must be at most ${URL_MAX_LENGTH} characters`)
    .refine((value) => value === '' || isHttpsUrl(value), 'Enter an absolute https:// URL')
    .optional(),
  routingKey: z
    .string()
    .trim()
    .max(PAGERDUTY_ROUTING_KEY_MAX_LENGTH, 'PagerDuty routing key is too long')
    .refine(
      (value) => value === '' || PAGERDUTY_ROUTING_KEY_PATTERN.test(value),
      'Enter a valid PagerDuty integration key (letters and digits)',
    )
    .optional(),
  secret: z
    .string()
    .trim()
    .max(WEBHOOK_SECRET_MAX_LENGTH, `Secret must be at most ${WEBHOOK_SECRET_MAX_LENGTH} characters`)
    .optional(),
  headers: webhookHeadersSchema.optional(),
  isActive: z.boolean(),
})

export type NotificationChannelUpdateValues = z.infer<typeof updateNotificationChannelFormSchema>
