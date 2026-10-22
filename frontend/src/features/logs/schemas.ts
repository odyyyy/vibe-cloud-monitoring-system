import { z } from 'zod'
import { MAX_RANGE_DAYS, rangeDurationMs } from '../metrics/timeRange'

export const LOG_LEVELS = ['DEBUG', 'INFO', 'WARN', 'ERROR'] as const
export const LOG_PAGE_SIZES = [25, 50, 100] as const
export const DEFAULT_LOG_PAGE_SIZE = LOG_PAGE_SIZES[0]

export const MAX_SEARCH_TEXT_LENGTH = 200
/** Mirrors the backend limit for exact-match facet filter values. */
export const MAX_FACET_VALUE_LENGTH = 256
export const PAGE_SIZE_MIN = 1
export const PAGE_SIZE_MAX = 100

const instant = z.string().refine((value) => !Number.isNaN(Date.parse(value)), 'Invalid timestamp')

const facetValue = z
  .string()
  .trim()
  .max(MAX_FACET_VALUE_LENGTH, `Filter values must be at most ${MAX_FACET_VALUE_LENGTH} characters`)
  .optional()

export const logLevelSchema = z.enum(LOG_LEVELS)

export const logsQuerySchema = z
  .object({
    applicationId: z.coerce
      .number({ invalid_type_error: 'Select an application' })
      .int()
      .positive('Select an application'),
    level: logLevelSchema.optional(),
    from: instant,
    to: instant,
    searchText: z
      .string()
      .trim()
      .max(MAX_SEARCH_TEXT_LENGTH, `Search text must be at most ${MAX_SEARCH_TEXT_LENGTH} characters`)
      .optional(),
    host: facetValue,
    service: facetValue,
    namespace: facetValue,
    page: z.coerce
      .number({ invalid_type_error: 'Invalid page' })
      .int('Invalid page')
      .min(0, 'Page must be 0 or greater'),
    size: z.coerce
      .number({ invalid_type_error: 'Invalid page size' })
      .int('Invalid page size')
      .min(PAGE_SIZE_MIN, 'Page size must be at least 1')
      .max(PAGE_SIZE_MAX, `Page size must be at most ${PAGE_SIZE_MAX}`),
  })
  .refine((values) => new Date(values.from).getTime() < new Date(values.to).getTime(), {
    message: 'The start of the range must be before the end',
    path: ['to'],
  })
  .refine(
    (values) => {
      const duration = rangeDurationMs(values.from, values.to)
      return duration !== null && duration <= MAX_RANGE_DAYS * 24 * 60 * 60 * 1000
    },
    {
      message: `Time ranges longer than ${MAX_RANGE_DAYS} days are not allowed`,
      path: ['to'],
    },
  )

export type LogsQueryFormValues = z.infer<typeof logsQuerySchema>
