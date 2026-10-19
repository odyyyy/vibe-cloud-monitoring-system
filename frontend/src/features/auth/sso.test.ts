import { describe, expect, it } from 'vitest'
import { getSsoLoginUrl, parseSsoToken } from './sso'

describe('getSsoLoginUrl', () => {
  it('points at the backend SSO entry point', () => {
    expect(getSsoLoginUrl()).toBe('/api/auth/sso/login')
  })
})

describe('parseSsoToken', () => {
  it('extracts a token from the URL fragment', () => {
    expect(parseSsoToken('#token=header.payload.signature')).toBe('header.payload.signature')
  })

  it('accepts a fragment without the leading hash', () => {
    expect(parseSsoToken('token=abc')).toBe('abc')
  })

  it('decodes percent-encoded tokens', () => {
    expect(parseSsoToken('#token=a%20b')).toBe('a b')
  })

  it('ignores fragments that do not carry a token', () => {
    expect(parseSsoToken('#access_token=abc')).toBeNull()
  })

  it('returns null for an empty or missing fragment', () => {
    expect(parseSsoToken('')).toBeNull()
    expect(parseSsoToken('#')).toBeNull()
  })

  it('returns null when the token value is empty', () => {
    expect(parseSsoToken('#token=')).toBeNull()
  })
})
