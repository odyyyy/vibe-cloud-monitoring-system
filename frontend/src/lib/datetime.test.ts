import { describe, expect, it } from 'vitest'
import { parseUtcInstant } from './datetime'

describe('parseUtcInstant', () => {
  it('treats a timezone-less backend timestamp as UTC', () => {
    const date = parseUtcInstant('2024-05-01T10:00:00')
    expect(date?.toISOString()).toBe('2024-05-01T10:00:00.000Z')
  })

  it('respects an explicit offset', () => {
    const date = parseUtcInstant('2024-05-01T10:00:00+02:00')
    expect(date?.toISOString()).toBe('2024-05-01T08:00:00.000Z')
  })

  it('returns null for an invalid value', () => {
    expect(parseUtcInstant('not-a-date')).toBeNull()
  })

  it('returns null for an empty value', () => {
    expect(parseUtcInstant('')).toBeNull()
  })
})
