import { z } from 'zod'

export const HEALTH_CHECK_METHODS = ['GET', 'POST'] as const
export type HealthCheckMethodValue = (typeof HEALTH_CHECK_METHODS)[number]

export const HEALTH_CHECK_TYPES = ['SINGLE_HTTP', 'MULTI_STEP_API'] as const
export type HealthCheckTypeValue = (typeof HEALTH_CHECK_TYPES)[number]

export const SYNTHETIC_HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD'] as const
export type SyntheticHttpMethodValue = (typeof SYNTHETIC_HTTP_METHODS)[number]

export const ASSERTION_TYPES = ['STATUS_CODE', 'BODY_CONTAINS', 'JSON_PATH'] as const
export type AssertionTypeValue = (typeof ASSERTION_TYPES)[number]

export const ASSERTION_OPERATORS: Record<AssertionTypeValue, readonly string[]> = {
  STATUS_CODE: ['==', '!='],
  BODY_CONTAINS: ['contains', 'not_contains'],
  JSON_PATH: ['==', '!=', 'contains', 'not_contains'],
}

export const MAX_STEPS = 5
export const MAX_HEADERS_PER_STEP = 20
export const MAX_EXTRACTIONS_PER_STEP = 10
export const MAX_ASSERTIONS_PER_STEP = 10
export const MAX_STEP_BODY_LENGTH = 16384
export const MAX_STEP_NAME_LENGTH = 100
export const MAX_HEADER_VALUE_LENGTH = 2048
export const MAX_VARIABLE_NAME_LENGTH = 64
export const MAX_EXTRACTION_EXPRESSION_LENGTH = 300
export const MAX_ASSERTION_VALUE_LENGTH = 500

export const NAME_MAX_LENGTH = 100
export const URL_MAX_LENGTH = 2048
export const MIN_INTERVAL_SECONDS = 10
export const MAX_INTERVAL_SECONDS = 86400
export const MIN_STATUS_CODE = 100
export const MAX_STATUS_CODE = 599
export const DEFAULT_EXPECTED_STATUS_CODE = 200
export const DEFAULT_INTERVAL_SECONDS = 60
export const DEFAULT_METHOD: HealthCheckMethodValue = 'GET'
export const DEFAULT_STEP_METHOD: SyntheticHttpMethodValue = 'GET'
export const DEFAULT_ASSERTION_TYPE: AssertionTypeValue = 'STATUS_CODE'

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

/** Mirrors the backend's deliberately small JSONPath subset (dot members and array indexes). */
export function isSimpleJsonPath(value: string): boolean {
  return /^\$(?:\.[A-Za-z0-9_\- ]+|\[(?:\d+|'[^']{1,128}'|"[^"]{1,128}")\])*$/.test(value.trim())
}

export function isExtractionExpression(value: string): boolean {
  const expression = value.trim()
  if (expression.startsWith('jsonpath:')) {
    return isSimpleJsonPath(expression.substring('jsonpath:'.length))
  }
  if (expression.startsWith('regex:')) {
    return expression.length > 'regex:'.length && expression.length <= MAX_EXTRACTION_EXPRESSION_LENGTH
  }
  return false
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

const headerNameSchema = z
  .string()
  .trim()
  .min(1, 'Header name is required')
  .regex(/^[A-Za-z0-9!#$%&'*+.^_|~-]+$/, 'Header name contains unsupported characters')

const variableNameSchema = z
  .string()
  .trim()
  .min(1, 'Variable name is required')
  .regex(/^[A-Za-z_][A-Za-z0-9_]{0,63}$/, 'Use letters, digits and underscores; start with a letter or underscore')
  .max(MAX_VARIABLE_NAME_LENGTH)

const stepNameSchema = z
  .string()
  .trim()
  .min(1, 'Step name is required')
  .max(MAX_STEP_NAME_LENGTH, `Step name must be at most ${MAX_STEP_NAME_LENGTH} characters`)

const extractionExpressionSchema = z
  .string()
  .trim()
  .min(1, 'Extraction expression is required')
  .max(MAX_EXTRACTION_EXPRESSION_LENGTH, 'Extraction expression is too long')
  .refine(isExtractionExpression, 'Use jsonpath:$.field or regex:<pattern>')

const assertionSchema = z
  .object({
    type: z.enum(ASSERTION_TYPES),
    operator: z.string().trim().min(1, 'Operator is required'),
    value: z.union([z.string().max(MAX_ASSERTION_VALUE_LENGTH), z.number()]),
    path: z.string().trim().max(256).optional(),
  })
  .superRefine((assertion, ctx) => {
    if (!ASSERTION_OPERATORS[assertion.type].includes(assertion.operator)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Unsupported operator for this assertion' })
    }
    if (assertion.type === 'STATUS_CODE') {
      const status = typeof assertion.value === 'number' ? assertion.value : Number(assertion.value)
      if (!Number.isInteger(status) || status < MIN_STATUS_CODE || status > MAX_STATUS_CODE) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: `Status code must be between ${MIN_STATUS_CODE} and ${MAX_STATUS_CODE}`,
        })
      }
    } else if (typeof assertion.value !== 'string' || assertion.value.length === 0) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Assertion value is required' })
    }
    if (assertion.type === 'JSON_PATH' && (!assertion.path || !isSimpleJsonPath(assertion.path))) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Enter a simple JSON path like $.status' })
    }
  })

export const syntheticStepSchema = z
  .object({
    name: stepNameSchema,
    method: z.enum(SYNTHETIC_HTTP_METHODS),
    url: targetUrlSchema,
    headers: z.record(headerNameSchema, z.string().max(MAX_HEADER_VALUE_LENGTH)).default({}),
    body: z.string().max(MAX_STEP_BODY_LENGTH, 'Request body is too long').optional(),
    extractVariables: z.record(variableNameSchema, extractionExpressionSchema).default({}),
    assertions: z.array(assertionSchema).max(MAX_ASSERTIONS_PER_STEP).default([]),
  })
  .superRefine((step, ctx) => {
    if ((step.method === 'GET' || step.method === 'HEAD') && step.body && step.body.length > 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `${step.method} steps must not define a body`,
        path: ['body'],
      })
    }
    if (Object.keys(step.headers).length > MAX_HEADERS_PER_STEP) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: `At most ${MAX_HEADERS_PER_STEP} headers are supported` })
    }
    if (Object.keys(step.extractVariables).length > MAX_EXTRACTIONS_PER_STEP) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `At most ${MAX_EXTRACTIONS_PER_STEP} extractions are supported`,
      })
    }
  })

export type SyntheticStepValues = z.infer<typeof syntheticStepSchema>

export const multiStepHealthCheckFormSchema = z.object({
  name: nameSchema,
  type: z.literal('MULTI_STEP_API'),
  intervalSeconds: intervalSecondsSchema,
  isActive: z.boolean(),
  steps: z
    .array(syntheticStepSchema)
    .min(1, 'Add at least one step')
    .max(MAX_STEPS, `At most ${MAX_STEPS} steps are supported`),
})

export type MultiStepHealthCheckFormValues = z.infer<typeof multiStepHealthCheckFormSchema>
