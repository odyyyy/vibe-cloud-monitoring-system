import { describe, expect, it } from 'vitest'
import { changePasswordSchema, loginSchema, registerSchema } from './schemas'

describe('loginSchema', () => {
  it('rejects empty credentials', () => {
    expect(loginSchema.safeParse({ username: '', password: '' }).success).toBe(false)
  })

  it('accepts non-empty credentials', () => {
    expect(loginSchema.safeParse({ username: 'ada', password: 'secret' }).success).toBe(true)
  })
})

describe('registerSchema', () => {
  const valid = {
    username: 'ada',
    email: 'ada@example.com',
    password: 'supersecret',
    confirmPassword: 'supersecret',
  }

  it('accepts a valid registration', () => {
    expect(registerSchema.safeParse(valid).success).toBe(true)
  })

  it('rejects mismatched passwords', () => {
    const result = registerSchema.safeParse({ ...valid, confirmPassword: 'different1' })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.some((issue) => issue.path.includes('confirmPassword'))).toBe(true)
    }
  })

  it('rejects an invalid email', () => {
    expect(registerSchema.safeParse({ ...valid, email: 'not-an-email' }).success).toBe(false)
  })
})

describe('changePasswordSchema', () => {
  const valid = {
    currentPassword: 'oldsecret',
    newPassword: 'newsecret1',
    confirmPassword: 'newsecret1',
  }

  it('accepts a valid change', () => {
    expect(changePasswordSchema.safeParse(valid).success).toBe(true)
  })

  it('rejects mismatched confirmation', () => {
    const result = changePasswordSchema.safeParse({ ...valid, confirmPassword: 'different1' })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.some((issue) => issue.path.includes('confirmPassword'))).toBe(true)
    }
  })

  it('rejects a new password identical to the current one', () => {
    const result = changePasswordSchema.safeParse({
      currentPassword: 'samepass1',
      newPassword: 'samepass1',
      confirmPassword: 'samepass1',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.some((issue) => issue.path.includes('newPassword'))).toBe(true)
    }
  })

  it('rejects a too-short new password', () => {
    expect(changePasswordSchema.safeParse({ ...valid, newPassword: 'short', confirmPassword: 'short' }).success).toBe(
      false,
    )
  })
})
