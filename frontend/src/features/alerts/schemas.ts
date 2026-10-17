import { z } from 'zod'

export const ALERT_RULE_TYPES = ['METRIC_THRESHOLD', 'HEALTH_CHECK'] as const
export type AlertRuleTypeValue = (typeof ALERT_RULE_TYPES)[number]

export const ALERT_CONDITIONS = ['GREATER_THAN', 'LESS_THAN', 'EQUAL'] as const
export type AlertConditionValue = (typeof ALERT_CONDITIONS)[number]

export const NOTIFICATION_CHANNEL_TYPES = ['WEBHOOK'] as const
export type NotificationChannelTypeValue = (typeof NOTIFICATION_CHANNEL_TYPES)[number]

export const NAME_MAX_LENGTH = 100
export const TARGET_MAX_LENGTH = 255
export const URL_MAX_LENGTH = 2048
export const MIN_DURATION_MINUTES = 1
export const MAX_DURATION_MINUTES = 1440
export const DEFAULT_DURATION_MINUTES = 5
export const DEFAULT_THRESHOLD = 90
export const DEFAULT_RULE_TYPE: AlertRuleTypeValue = 'METRIC_THRESHOLD'
export const DEFAULT_CONDITION: AlertConditionValue = 'GREATER_THAN'

/**
 * Frontend webhook URL validation is a UX aid, not a security boundary. It only checks that the
 * URL is absolute, uses https and has a host; the backend enforces full SSRF protection.
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

export const alertRuleFormSchema = z
  .object({
    name: nameSchema,
    type: z.enum(ALERT_RULE_TYPES),
    applicationId: z.number().int().positive().nullable(),
    target: targetSchema,
    condition: z.enum(ALERT_CONDITIONS),
    threshold: thresholdSchema,
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
    if (values.type === 'HEALTH_CHECK' && !/^\d+$/.test(values.target)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['target'],
        message: 'Select a health check',
      })
    }
  })

export type AlertRuleFormValues = z.infer<typeof alertRuleFormSchema>

export const notificationChannelFormSchema = z.object({
  type: z.enum(NOTIFICATION_CHANNEL_TYPES),
  url: z
    .string()
    .trim()
    .min(1, 'Webhook URL is required')
    .max(URL_MAX_LENGTH, `Webhook URL must be at most ${URL_MAX_LENGTH} characters`)
    .refine(isHttpsUrl, 'Enter an absolute https:// URL'),
  isActive: z.boolean(),
})

export type NotificationChannelFormValues = z.infer<typeof notificationChannelFormSchema>
