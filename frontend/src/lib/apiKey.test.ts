import { describe, expect, it } from 'vitest'
import { maskApiKey } from './apiKey'

describe('maskApiKey', () => {
  it('keeps a short prefix and hides the rest', () => {
    const masked = maskApiKey('app_1234567890abcdef')
    expect(masked.startsWith('app_')).toBe(true)
    expect(masked).not.toContain('1234567890abcdef')
  })

  it('returns an empty string for an empty key', () => {
    expect(maskApiKey('')).toBe('')
  })

  it('never reveals keys shorter than the visible prefix', () => {
    expect(maskApiKey('ab')).toBe('**')
  })
})
