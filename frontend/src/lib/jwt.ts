export interface JwtClaims {
  sub?: string
  uid?: number
  iat?: number
  exp?: number
}

function base64UrlDecode(value: string): string {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=')
  return atob(padded)
}

export function decodeJwt(token: string): JwtClaims | null {
  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }
  const payload = parts[1]
  if (!payload) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(base64UrlDecode(payload))
    if (typeof parsed !== 'object' || parsed === null) {
      return null
    }
    return parsed as JwtClaims
  } catch {
    return null
  }
}

/**
 * Returns true when the token carries an `exp` claim in the past.
 * A token without a decodable `exp` is treated as not expired so the server
 * remains the source of truth (a rejected request triggers logout via 401).
 */
export function isTokenExpired(token: string, skewSeconds = 0): boolean {
  const claims = decodeJwt(token)
  if (!claims || typeof claims.exp !== 'number') {
    return false
  }
  return claims.exp * 1000 <= Date.now() + skewSeconds * 1000
}
