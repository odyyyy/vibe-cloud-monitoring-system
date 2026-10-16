import { describe, expect, it } from 'vitest'
import { healthCheckFormSchema, isHttpUrl } from './schemas'

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
