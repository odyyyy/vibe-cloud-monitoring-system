import { describe, expect, it } from 'vitest'
import { decodeJwt, isTokenExpired } from './jwt'

function tokenWithClaims(claims: Record<string, unknown>): string {
  const encode = (value: unknown): string =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(claims)}.signature`
}

describe('decodeJwt', () => {
  it('returns null for malformed tokens', () => {
    expect(decodeJwt('not-a-jwt')).toBeNull()
  })

  it('decodes claims from a well-formed token', () => {
    expect(decodeJwt(tokenWithClaims({ sub: 'ada', uid: 7 }))).toMatchObject({ sub: 'ada', uid: 7 })
  })
})

describe('isTokenExpired', () => {
  it('treats a past exp as expired', () => {
    const past = Math.floor(Date.now() / 1000) - 60
    expect(isTokenExpired(tokenWithClaims({ sub: 'ada', exp: past }))).toBe(true)
  })

  it('treats a future exp as valid', () => {
    const future = Math.floor(Date.now() / 1000) + 3600
    expect(isTokenExpired(tokenWithClaims({ sub: 'ada', exp: future }))).toBe(false)
  })

  it('treats a token without exp as not expired', () => {
    expect(isTokenExpired(tokenWithClaims({ sub: 'ada' }))).toBe(false)
  })

  it('treats an undecodable token as not expired', () => {
    expect(isTokenExpired('garbage')).toBe(false)
  })
})
