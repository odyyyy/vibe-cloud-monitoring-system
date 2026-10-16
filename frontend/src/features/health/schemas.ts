import { z } from 'zod'

export const HEALTH_CHECK_METHODS = ['GET', 'POST'] as const
export type HealthCheckMethodValue = (typeof HEALTH_CHECK_METHODS)[number]

export const NAME_MAX_LENGTH = 100
export const URL_MAX_LENGTH = 2048
export const MIN_INTERVAL_SECONDS = 10
export const MAX_INTERVAL_SECONDS = 86400
export const MIN_STATUS_CODE = 100
export const MAX_STATUS_CODE = 599
export const DEFAULT_EXPECTED_STATUS_CODE = 200
export const DEFAULT_INTERVAL_SECONDS = 60
export const DEFAULT_METHOD: HealthCheckMethodValue = 'GET'

/**
 * Frontend URL validation is a UX aid, not a security boundary. It only checks the scheme and
 * that a host is present; the backend enforces SSRF protection.
 */
export function isHttpUrl(value: string): boolean {
  let url: URL
  try {
    url = new URL(value)
  } catch {
    return false
  }
  return (url.protocol === 'http:' || url.protocol === 'https:') && url.hostname.length > 0
}

const nameSchema = z
  .string()
  .trim()
  .min(1, 'Name is required')
  .max(NAME_MAX_LENGTH, `Name must be at most ${NAME_MAX_LENGTH} characters`)

const targetUrlSchema = z
  .string()
  .trim()
  .min(1, 'Target URL is required')
  .max(URL_MAX_LENGTH, `Target URL must be at most ${URL_MAX_LENGTH} characters`)
  .refine(isHttpUrl, 'Enter an absolute http:// or https:// URL')

const methodSchema = z.enum(HEALTH_CHECK_METHODS)

const expectedStatusCodeSchema = z.coerce
  .number({ invalid_type_error: 'Expected status code is required' })
  .int('Expected status code must be a whole number')
  .min(MIN_STATUS_CODE, `Expected status code must be between ${MIN_STATUS_CODE} and ${MAX_STATUS_CODE}`)
  .max(MAX_STATUS_CODE, `Expected status code must be between ${MIN_STATUS_CODE} and ${MAX_STATUS_CODE}`)

const intervalSecondsSchema = z.coerce
  .number({ invalid_type_error: 'Interval is required' })
  .int('Interval must be a whole number')
  .min(MIN_INTERVAL_SECONDS, `Interval must be at least ${MIN_INTERVAL_SECONDS} seconds`)
  .max(MAX_INTERVAL_SECONDS, `Interval must be at most ${MAX_INTERVAL_SECONDS} seconds`)

export const healthCheckFormSchema = z.object({
  name: nameSchema,
  targetUrl: targetUrlSchema,
  method: methodSchema,
  expectedStatusCode: expectedStatusCodeSchema,
  intervalSeconds: intervalSecondsSchema,
  isActive: z.boolean(),
})

export type HealthCheckFormValues = z.infer<typeof healthCheckFormSchema>
