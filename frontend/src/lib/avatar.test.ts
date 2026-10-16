import { describe, expect, it } from 'vitest'
import { avatarInitial } from './avatar'

describe('avatarInitial', () => {
  it('returns the first letter uppercased', () => {
    expect(avatarInitial('ada')).toBe('A')
  })

  it('trims surrounding whitespace before taking the initial', () => {
    expect(avatarInitial('  grace  ')).toBe('G')
  })

  it('falls back to a placeholder for empty input', () => {
    expect(avatarInitial('')).toBe('?')
    expect(avatarInitial('   ')).toBe('?')
    expect(avatarInitial(null)).toBe('?')
    expect(avatarInitial(undefined)).toBe('?')
  })
})
