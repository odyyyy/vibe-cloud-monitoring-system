import { z } from 'zod'
import { MAX_RANGE_DAYS, rangeDurationMs } from './timeRange'

const instant = z.string().refine((value) => !Number.isNaN(Date.parse(value)), 'Invalid timestamp')

export const metricNameSchema = z.string().trim().min(1, 'Metric name is required').max(255, 'Metric name is too long')

export const metricsQuerySchema = z
  .object({
    applicationId: z.coerce
      .number({ invalid_type_error: 'Select an application' })
      .int()
      .positive('Select an application'),
    metricName: metricNameSchema,
    from: instant,
    to: instant,
    aggregation: z.enum(['AVG', 'MAX', 'MIN', 'SUM']),
    intervalMinutes: z.coerce
      .number({ invalid_type_error: 'Select an interval' })
      .int()
      .positive('Interval must be greater than 0'),
  })
  .refine((values) => new Date(values.from).getTime() < new Date(values.to).getTime(), {
    message: 'The start of the range must be before the end',
    path: ['to'],
  })
  .refine((values) => {
    const duration = rangeDurationMs(values.from, values.to)
    return duration !== null && duration <= MAX_RANGE_DAYS * 24 * 60 * 60 * 1000
  }, {
    message: `Time ranges longer than ${MAX_RANGE_DAYS} days are not allowed`,
    path: ['to'],
  })

export type MetricsQueryFormValues = z.infer<typeof metricsQuerySchema>
