import { z } from 'zod'

export const AUDIT_PAGE_SIZES = [25, 50, 100] as const
export const DEFAULT_AUDIT_PAGE_SIZE = 50
export const PAGE_SIZE_MIN = 1
export const PAGE_SIZE_MAX = 100
export const MAX_ACTION_LENGTH = 64
export const MAX_RANGE_DAYS = 366

const instant = z.string().refine((value) => !Number.isNaN(Date.parse(value)), 'Invalid timestamp')

/**
 * Mirrors the backend audit query contract: every filter is optional, the range is
 * inclusive, must be positive and must not exceed {@link MAX_RANGE_DAYS} days.
 */
export const auditQuerySchema = z
  .object({
    action: z
      .string()
      .trim()
      .max(MAX_ACTION_LENGTH, `Action must be at most ${MAX_ACTION_LENGTH} characters`)
      .optional(),
    from: instant.optional(),
    to: instant.optional(),
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
  .refine(
    (values) =>
      !values.from || !values.to || new Date(values.from).getTime() < new Date(values.to).getTime(),
    {
      message: 'The start of the range must be before the end',
      path: ['to'],
    },
  )
  .refine(
    (values) => {
      if (!values.from || !values.to) {
        return true
      }
      const duration = new Date(values.to).getTime() - new Date(values.from).getTime()
      return duration <= MAX_RANGE_DAYS * 24 * 60 * 60 * 1000
    },
    {
      message: `Time ranges longer than ${MAX_RANGE_DAYS} days are not allowed`,
      path: ['to'],
    },
  )

export type AuditQueryFormValues = z.infer<typeof auditQuerySchema>
