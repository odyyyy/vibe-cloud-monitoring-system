import { describe, expect, it } from 'vitest'
import { PAT_MAX_EXPIRES_DAYS, PAT_MIN_EXPIRES_DAYS, patFormSchema } from './schemas'

function parse(overrides: Record<string, unknown> = {}) {
  return patFormSchema.safeParse({
    name: 'ci',
    expiresDays: 30,
    scopes: ['read:metrics'],
    ...overrides,
  })
}

describe('patFormSchema', () => {
  it('accepts a valid token definition and trims the name', () => {
    const result = parse({ name: '  ci-deploy  ' })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.name).toBe('ci-deploy')
      expect(result.data.expiresDays).toBe(30)
    }
  })

  it('requires a name', () => {
    expect(parse({ name: '' }).success).toBe(false)
    expect(parse({ name: '   ' }).success).toBe(false)
  })

  it('rejects an over-long name', () => {
    expect(parse({ name: 'x'.repeat(101) }).success).toBe(false)
  })

  it('bounds the expiration between the minimum and maximum days', () => {
    expect(parse({ expiresDays: PAT_MIN_EXPIRES_DAYS }).success).toBe(true)
    expect(parse({ expiresDays: PAT_MAX_EXPIRES_DAYS }).success).toBe(true)
    expect(parse({ expiresDays: 0 }).success).toBe(false)
    expect(parse({ expiresDays: PAT_MAX_EXPIRES_DAYS + 1 }).success).toBe(false)
  })

  it('requires at least one scope', () => {
    expect(parse({ scopes: [] }).success).toBe(false)
  })

  it('rejects unknown scopes', () => {
    expect(parse({ scopes: ['read:everything'] }).success).toBe(false)
  })
})
