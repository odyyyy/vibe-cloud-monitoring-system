import { describe, expect, it } from 'vitest'
import { updateEmailSchema, updateUsernameSchema } from './schemas'

describe('updateUsernameSchema', () => {
  it('accepts a valid username and password', () => {
    expect(
      updateUsernameSchema.safeParse({ username: 'grace_hopper-1', currentPassword: 'supersecret' }).success,
    ).toBe(true)
  })

  it('rejects short usernames', () => {
    const result = updateUsernameSchema.safeParse({ username: 'ab', currentPassword: 'supersecret' })
    expect(result.success).toBe(false)
  })

  it('rejects usernames with disallowed characters', () => {
    const result = updateUsernameSchema.safeParse({ username: 'grace hopper', currentPassword: 'supersecret' })
    expect(result.success).toBe(false)
  })

  it('requires the current password', () => {
    const result = updateUsernameSchema.safeParse({ username: 'grace', currentPassword: '' })
    expect(result.success).toBe(false)
  })
})

describe('updateEmailSchema', () => {
  it('accepts a valid email and password', () => {
    expect(updateEmailSchema.safeParse({ newEmail: 'new@example.com', currentPassword: 'supersecret' }).success).toBe(
      true,
    )
  })

  it('rejects invalid emails', () => {
    const result = updateEmailSchema.safeParse({ newEmail: 'not-an-email', currentPassword: 'supersecret' })
    expect(result.success).toBe(false)
  })

  it('requires the current password', () => {
    const result = updateEmailSchema.safeParse({ newEmail: 'new@example.com', currentPassword: '' })
    expect(result.success).toBe(false)
  })
})
