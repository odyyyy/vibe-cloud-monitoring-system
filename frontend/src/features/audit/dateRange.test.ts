import { describe, expect, it } from 'vitest'
import { instantToLocalInput, localInputToInstant } from './dateRange'

describe('localInputToInstant', () => {
  it('converts a local datetime-local value into a UTC instant', () => {
    const value = '2024-05-01T10:15'
    expect(localInputToInstant(value)).toBe(new Date(value).toISOString())
  })

  it('returns an empty string for an empty value', () => {
    expect(localInputToInstant('')).toBe('')
  })

  it('returns an empty string for an unparseable value', () => {
    expect(localInputToInstant('not-a-date')).toBe('')
  })
})

describe('instantToLocalInput', () => {
  it('round-trips a local datetime-local value through an instant', () => {
    const value = '2024-05-01T10:15'
    expect(instantToLocalInput(localInputToInstant(value))).toBe(value)
  })

  it('returns an empty string for empty or invalid input', () => {
    expect(instantToLocalInput('')).toBe('')
    expect(instantToLocalInput('not-a-date')).toBe('')
  })
})
