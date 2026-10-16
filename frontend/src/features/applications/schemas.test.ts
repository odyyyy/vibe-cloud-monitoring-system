import { describe, expect, it } from 'vitest'
import { applicationFormSchema } from './schemas'

describe('applicationFormSchema', () => {
  it('accepts a trimmed valid name', () => {
    const result = applicationFormSchema.safeParse({ name: '  My app  ' })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.name).toBe('My app')
    }
  })

  it('rejects an empty name', () => {
    expect(applicationFormSchema.safeParse({ name: '   ' }).success).toBe(false)
  })

  it('rejects names longer than 100 characters', () => {
    expect(applicationFormSchema.safeParse({ name: 'a'.repeat(101) }).success).toBe(false)
  })
})
