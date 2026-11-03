import { describe, expect, it } from 'vitest'
import {
  healthCheckFormSchema,
  isHttpUrl,
  isSimpleJsonPath,
  multiStepHealthCheckFormSchema,
} from './schemas'

function validValues(overrides: Record<string, unknown> = {}) {
  return {
    name: 'API health',
    targetUrl: 'https://api.example.com/health',
    method: 'GET',
    expectedStatusCode: '200',
    intervalSeconds: '60',
    isActive: true,
    ...overrides,
  }
}

function validStep(overrides: Record<string, unknown> = {}) {
  return {
    name: 'Login',
    method: 'POST',
    url: 'https://api.example.com/login',
    headers: { 'Content-Type': 'application/json' },
    body: '{"user":"demo"}',
    extractVariables: { token: 'jsonpath:$.access_token' },
    assertions: [{ type: 'STATUS_CODE', operator: '==', value: 200 }],
    ...overrides,
  }
}

function validWorkflow(overrides: Record<string, unknown> = {}) {
  return {
    name: 'Login flow',
    type: 'MULTI_STEP_API',
    intervalSeconds: '60',
    isActive: true,
    steps: [validStep()],
    ...overrides,
  }
}

describe('isHttpUrl', () => {
  it('accepts http and https URLs', () => {
    expect(isHttpUrl('http://example.com')).toBe(true)
    expect(isHttpUrl('https://example.com/health?a=1')).toBe(true)
  })

  it('rejects non-http schemes', () => {
    expect(isHttpUrl('ftp://example.com')).toBe(false)
    expect(isHttpUrl('file:///etc/passwd')).toBe(false)
    expect(isHttpUrl('gopher://example.com')).toBe(false)
    expect(isHttpUrl('javascript:alert(1)')).toBe(false)
  })

  it('rejects values that are not absolute URLs', () => {
    expect(isHttpUrl('example.com')).toBe(false)
    expect(isHttpUrl('')).toBe(false)
  })
})

describe('healthCheckFormSchema', () => {
  it('accepts valid values and coerces numbers', () => {
    const result = healthCheckFormSchema.safeParse(validValues())
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.expectedStatusCode).toBe(200)
      expect(result.data.intervalSeconds).toBe(60)
      expect(result.data.method).toBe('GET')
    }
  })

  it('trims the name and target URL', () => {
    const result = healthCheckFormSchema.safeParse(
      validValues({ name: '  spaced  ', targetUrl: '  https://example.com  ' }),
    )
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.name).toBe('spaced')
      expect(result.data.targetUrl).toBe('https://example.com')
    }
  })

  it('rejects a blank name', () => {
    expect(healthCheckFormSchema.safeParse(validValues({ name: '   ' })).success).toBe(false)
  })

  it('rejects non-http target URLs', () => {
    expect(healthCheckFormSchema.safeParse(validValues({ targetUrl: 'file:///etc/passwd' })).success).toBe(false)
    expect(healthCheckFormSchema.safeParse(validValues({ targetUrl: 'ftp://example.com' })).success).toBe(false)
  })

  it('rejects intervals below 10 seconds', () => {
    expect(healthCheckFormSchema.safeParse(validValues({ intervalSeconds: '5' })).success).toBe(false)
  })

  it('rejects status codes outside 100-599', () => {
    expect(healthCheckFormSchema.safeParse(validValues({ expectedStatusCode: '99' })).success).toBe(false)
    expect(healthCheckFormSchema.safeParse(validValues({ expectedStatusCode: '600' })).success).toBe(false)
  })

  it('rejects an unknown method', () => {
    expect(healthCheckFormSchema.safeParse(validValues({ method: 'DELETE' })).success).toBe(false)
  })

  it('rejects names longer than 100 characters', () => {
    expect(healthCheckFormSchema.safeParse(validValues({ name: 'a'.repeat(101) })).success).toBe(false)
  })
})

describe('isSimpleJsonPath', () => {
  it('accepts dot members and array indexes', () => {
    expect(isSimpleJsonPath('$.access_token')).toBe(true)
    expect(isSimpleJsonPath('$.data.items[0].id')).toBe(true)
    expect(isSimpleJsonPath("$['access-token']")).toBe(true)
  })

  it('rejects recursive descent, wildcards and filters', () => {
    expect(isSimpleJsonPath('$..token')).toBe(false)
    expect(isSimpleJsonPath('$.*')).toBe(false)
    expect(isSimpleJsonPath('$.items[?(@.id==1)]')).toBe(false)
    expect(isSimpleJsonPath('access_token')).toBe(false)
  })
})

describe('multiStepHealthCheckFormSchema', () => {
  it('accepts a two-step workflow with extraction and assertions', () => {
    const result = multiStepHealthCheckFormSchema.safeParse(
      validWorkflow({
        steps: [
          validStep(),
          {
            name: 'Fetch data',
            method: 'GET',
            url: 'https://api.example.com/data',
            headers: { Authorization: 'Bearer {{token}}' },
            assertions: [
              { type: 'STATUS_CODE', operator: '==', value: '200' },
              { type: 'JSON_PATH', operator: '==', path: '$.status', value: 'ok' },
            ],
          },
        ],
      }),
    )

    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.intervalSeconds).toBe(60)
      expect(result.data.steps).toHaveLength(2)
    }
  })

  it('rejects a workflow without steps', () => {
    expect(multiStepHealthCheckFormSchema.safeParse(validWorkflow({ steps: [] })).success).toBe(false)
  })

  it('rejects more than five steps', () => {
    const steps = Array.from({ length: 6 }, () => validStep({ name: 'Step' }))
    expect(multiStepHealthCheckFormSchema.safeParse(validWorkflow({ steps })).success).toBe(false)
  })

  it('rejects a GET step with a body', () => {
    expect(
      multiStepHealthCheckFormSchema.safeParse(
        validWorkflow({ steps: [validStep({ method: 'GET', body: '{}' })] }),
      ).success,
    ).toBe(false)
  })

  it('rejects unsupported extraction expressions', () => {
    expect(
      multiStepHealthCheckFormSchema.safeParse(
        validWorkflow({
          steps: [validStep({ extractVariables: { token: 'header:x-token' } })],
        }),
      ).success,
    ).toBe(false)
  })

  it('rejects invalid JSON path assertions', () => {
    expect(
      multiStepHealthCheckFormSchema.safeParse(
        validWorkflow({
          steps: [
            validStep({
              assertions: [{ type: 'JSON_PATH', operator: '==', path: '$..status', value: 'ok' }],
            }),
          ],
        }),
      ).success,
    ).toBe(false)
  })

  it('rejects status code assertions outside 100-599', () => {
    expect(
      multiStepHealthCheckFormSchema.safeParse(
        validWorkflow({
          steps: [
            validStep({
              assertions: [{ type: 'STATUS_CODE', operator: '==', value: 600 }],
            }),
          ],
        }),
      ).success,
    ).toBe(false)
  })
})
