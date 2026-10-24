import { z } from 'zod'
import { EMAIL_MAX_LENGTH, isEmailAddress } from '../alerts/schemas'
import type {
  ScheduledReportChannelType,
  ScheduledReportFrequency,
  ScheduledReportRequest,
} from '../../api/scheduledReports'

export const SCHEDULED_REPORT_FREQUENCIES = ['DAILY', 'WEEKLY', 'MONTHLY'] as const
export const SCHEDULED_REPORT_CHANNEL_TYPES = ['EMAIL', 'SLACK'] as const

export const SCHEDULED_REPORT_NAME_MAX_LENGTH = 100
export const SCHEDULED_REPORT_MAX_RECIPIENTS = 10
export const DEFAULT_SCHEDULED_REPORT_TIME = '09:00'
export const DEFAULT_SCHEDULED_REPORT_DAY = 1

const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/

const DAY_LABELS = [
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
  'Sunday',
] as const

export function scheduledReportFrequencyLabel(frequency: ScheduledReportFrequency): string {
  switch (frequency) {
    case 'DAILY':
      return 'Daily'
    case 'WEEKLY':
      return 'Weekly'
    case 'MONTHLY':
      return 'Monthly'
  }
}

export function scheduledReportChannelLabel(channelType: ScheduledReportChannelType): string {
  return channelType === 'EMAIL' ? 'Email' : 'Slack'
}

export function dayOfWeekLabel(dayOfWeek: number | null): string {
  if (dayOfWeek === null || dayOfWeek < 1 || dayOfWeek > 7) {
    return 'Monday'
  }
  return DAY_LABELS[dayOfWeek - 1]!
}

export function normalizedTimeOfDay(value: string): string {
  return value.slice(0, 5)
}

/** Human-readable recurring schedule in the report's configured time zone. */
export function scheduledReportSchedule(
  frequency: ScheduledReportFrequency,
  dayOfWeek: number | null,
  timeOfDay: string,
  timeZone: string,
): string {
  const time = normalizedTimeOfDay(timeOfDay)
  switch (frequency) {
    case 'DAILY':
      return `Every day at ${time} (${timeZone})`
    case 'WEEKLY':
      return `Every ${dayOfWeekLabel(dayOfWeek)} at ${time} (${timeZone})`
    case 'MONTHLY':
      return `Monthly on the 1st at ${time} (${timeZone})`
  }
}

/**
 * Splits the comma/newline separated recipient input, normalizes addresses, and removes
 * duplicates. Validation happens separately so the form can report individual problems.
 */
export function parseRecipientInput(input: string): string[] {
  const unique = new Set<string>()
  for (const raw of input.split(/[\n,;]+/)) {
    const email = raw.trim().toLowerCase()
    if (email.length > 0) {
      unique.add(email)
    }
  }
  return [...unique]
}

export function formatRecipients(recipients: string[]): string {
  return recipients.join(', ')
}

export const scheduledReportFormSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, 'Name is required')
      .max(
        SCHEDULED_REPORT_NAME_MAX_LENGTH,
        `Name must be at most ${SCHEDULED_REPORT_NAME_MAX_LENGTH} characters`,
      ),
    frequency: z.enum(SCHEDULED_REPORT_FREQUENCIES),
    dayOfWeek: z.number().int().min(1).max(7).nullable(),
    timeOfDay: z.string().regex(TIME_PATTERN, 'Enter a valid time'),
    channelType: z.enum(SCHEDULED_REPORT_CHANNEL_TYPES),
    channelId: z.number().int().positive().nullable(),
    recipients: z.string(),
    includeMetrics: z.boolean(),
    includeTopErrors: z.boolean(),
    includeUptime: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (values.frequency === 'WEEKLY' && values.dayOfWeek === null) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['dayOfWeek'],
        message: 'Choose a day of the week',
      })
    }

    if (values.channelType === 'SLACK' && values.channelId === null) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['channelId'],
        message: 'Choose a Slack channel',
      })
    }

    if (values.channelType === 'EMAIL') {
      const recipients = parseRecipientInput(values.recipients)
      if (recipients.length === 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['recipients'],
          message: 'Add at least one recipient',
        })
        return
      }
      if (recipients.length > SCHEDULED_REPORT_MAX_RECIPIENTS) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['recipients'],
          message: `At most ${SCHEDULED_REPORT_MAX_RECIPIENTS} recipients are allowed`,
        })
      }
      if (recipients.some((email) => email.length > EMAIL_MAX_LENGTH || !isEmailAddress(email))) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['recipients'],
          message: 'Every recipient must be a valid email address',
        })
      }
    }

    if (!values.includeMetrics && !values.includeTopErrors && !values.includeUptime) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['includeMetrics'],
        message: 'Include at least one section',
      })
    }
  })

export type ScheduledReportFormValues = z.infer<typeof scheduledReportFormSchema>

export function toScheduledReportRequest(
  values: ScheduledReportFormValues,
): ScheduledReportRequest {
  return {
    name: values.name.trim(),
    frequency: values.frequency,
    dayOfWeek: values.frequency === 'WEEKLY' ? values.dayOfWeek : null,
    timeOfDay: values.timeOfDay,
    channelType: values.channelType,
    channelId: values.channelType === 'SLACK' ? values.channelId : null,
    recipients:
      values.channelType === 'EMAIL' ? parseRecipientInput(values.recipients) : [],
    includeMetrics: values.includeMetrics,
    includeTopErrors: values.includeTopErrors,
    includeUptime: values.includeUptime,
  }
}
