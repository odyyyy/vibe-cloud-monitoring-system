import { z } from 'zod'

export const ALERT_RULE_TYPES = ['METRIC_THRESHOLD', 'HEALTH_CHECK'] as const
export type AlertRuleTypeValue = (typeof ALERT_RULE_TYPES)[number]

export const ALERT_CONDITIONS = ['GREATER_THAN', 'LESS_THAN', 'EQUAL'] as const
export type AlertConditionValue = (typeof ALERT_CONDITIONS)[number]

export const LOGIC_OPERATORS = ['AND', 'OR'] as const
export type LogicOperatorValue = (typeof LOGIC_OPERATORS)[number]

export const NOTIFICATION_CHANNEL_TYPES = ['WEBHOOK', 'SLACK', 'EMAIL'] as const
export type NotificationChannelTypeValue = (typeof NOTIFICATION_CHANNEL_TYPES)[number]

export const NAME_MAX_LENGTH = 100
export const TARGET_MAX_LENGTH = 255
export const URL_MAX_LENGTH = 2048
export const EMAIL_MAX_LENGTH = 320
export const MAX_CONDITIONS = 10
export const MIN_DURATION_MINUTES = 1
export const MAX_DURATION_MINUTES = 1440
export const DEFAULT_DURATION_MINUTES = 5
export const DEFAULT_THRESHOLD = 90
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
      .min(1, 'Add at least one condition')
      .max(MAX_CONDITIONS, `At most ${MAX_CONDITIONS} conditions are allowed`),
    logicOperator: z.enum(LOGIC_OPERATORS),
    durationMinutes: durationMinutesSchema,
    isActive: z.boolean(),
  })
  .superRefine((values, ctx) => {
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
  })

export type AlertRuleFormValues = z.infer<typeof alertRuleFormSchema>

const webhookChannelSchema = z.object({
  type: z.literal('WEBHOOK'),
  url: z
    .string()
    .trim()
    .min(1, 'Webhook URL is required')
    .max(URL_MAX_LENGTH, `Webhook URL must be at most ${URL_MAX_LENGTH} characters`)
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
])

export type NotificationChannelFormValues = z.infer<typeof notificationChannelFormSchema>
