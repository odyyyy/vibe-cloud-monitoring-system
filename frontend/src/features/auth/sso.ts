import { apiUrl } from '../../api/client'

/**
 * URL that starts the backend OIDC flow. The browser must be navigated to it (not fetched),
 * because the flow relies on a series of HTTP redirects.
 */
export function getSsoLoginUrl(): string {
  return apiUrl('/auth/sso/login')
}

/**
 * Reads the internal JWT that the backend appends to the redirect URL as a fragment
 * (`#token=...`). Returns null when the fragment is absent or does not carry a token.
 */
export function parseSsoToken(hash: string): string | null {
  const raw = hash.startsWith('#') ? hash.slice(1) : hash
  if (raw.length === 0) {
    return null
  }
  const token = new URLSearchParams(raw).get('token')
  return token !== null && token.length > 0 ? token : null
}
