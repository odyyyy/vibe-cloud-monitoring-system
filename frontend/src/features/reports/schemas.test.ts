import { describe, expect, it } from 'vitest'
import {
  parseRecipientInput,
  scheduledReportFormSchema,
  scheduledReportSchedule,
  toScheduledReportRequest,
} from './schemas'

const validValues = {
  name: '  Weekly operations digest  ',
  frequency: 'WEEKLY' as const,
  dayOfWeek: 1,
  timeOfDay: '09:30',
  channelType: 'EMAIL' as const,
  channelId: null,
  recipients: 'Ops@example.com, dev@example.com\nops@example.com',
  includeMetrics: true,
  includeTopErrors: true,
  includeUptime: true,
}

describe('parseRecipientInput', () => {
  it('normalizes, splits and deduplicates addresses', () => {
    expect(parseRecipientInput('Ops@example.com, dev@example.com\nops@example.com')).toEqual([
      'ops@example.com',
      'dev@example.com',
    ])
  })
})

describe('scheduledReportFormSchema', () => {
  it('accepts a complete weekly email report', () => {
    expect(scheduledReportFormSchema.safeParse(validValues).success).toBe(true)
  })

  it('requires a Slack channel for Slack reports', () => {
    const result = scheduledReportFormSchema.safeParse({
      ...validValues,
      channelType: 'SLACK',
      channelId: null,
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0]?.path).toEqual(['channelId'])
    }
  })

  it('rejects invalid email recipients', () => {
    const result = scheduledReportFormSchema.safeParse({
      ...validValues,
      recipients: 'not-an-email',
    })
    expect(result.success).toBe(false)
  })

  it('rejects more than ten recipients', () => {
    const recipients = Array.from({ length: 11 }, (_, index) => `user${index}@example.com`).join(',')
    const result = scheduledReportFormSchema.safeParse({ ...validValues, recipients })
    expect(result.success).toBe(false)
  })

  it('requires at least one report section', () => {
    const result = scheduledReportFormSchema.safeParse({
      ...validValues,
      includeMetrics: false,
      includeTopErrors: false,
      includeUptime: false,
    })
    expect(result.success).toBe(false)
  })

  it('requires a weekday for weekly reports', () => {
    const result = scheduledReportFormSchema.safeParse({ ...validValues, dayOfWeek: null })
    expect(result.success).toBe(false)
  })
})

describe('toScheduledReportRequest', () => {
  it('builds a normalized API payload without unused day-of-week data', () => {
    const parsed = scheduledReportFormSchema.parse(validValues)
    expect(toScheduledReportRequest(parsed)).toEqual({
      name: 'Weekly operations digest',
      frequency: 'WEEKLY',
      dayOfWeek: 1,
      timeOfDay: '09:30',
      channelType: 'EMAIL',
      channelId: null,
      recipients: ['ops@example.com', 'dev@example.com'],
      includeMetrics: true,
      includeTopErrors: true,
      includeUptime: true,
    })
  })
})

describe('scheduledReportSchedule', () => {
  it('describes daily, weekly and monthly schedules in the report time zone', () => {
    expect(scheduledReportSchedule('DAILY', null, '09:00:00', 'UTC')).toBe(
      'Every day at 09:00 (UTC)',
    )
    expect(scheduledReportSchedule('WEEKLY', 3, '18:05:00', 'Europe/Berlin')).toBe(
      'Every Wednesday at 18:05 (Europe/Berlin)',
    )
    expect(scheduledReportSchedule('MONTHLY', null, '07:00:00', 'UTC')).toBe(
      'Monthly on the 1st at 07:00 (UTC)',
    )
  })
})
