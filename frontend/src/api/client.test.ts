import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

function tokenWithClaims(claims: Record<string, unknown>): string {
  const encode = (value: unknown): string =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(claims)}.signature`
}

function okResponse(): Response {
  return {
    ok: true,
    status: 200,
    text: async () => '{}',
  } as Response
}

describe('apiRequest', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    window.localStorage.clear()
    useAuthStore.getState().logout()
    fetchMock.mockReset()
    fetchMock.mockResolvedValue(okResponse())
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends the active organization header', async () => {
    useAuthStore.getState().login(tokenWithClaims({ sub: 'ada', uid: 7 }))
    useAuthStore.getState().setActiveOrganization(42, 'VIEWER')

    await apiRequest('/v1/overview', { token: 'jwt-token' })

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.headers).toMatchObject({
      Authorization: 'Bearer jwt-token',
      'X-Organization-Id': '42',
    })
  })

  it('omits the organization header when no organization is active', async () => {
    await apiRequest('/auth/login', { method: 'POST', body: { username: 'ada' } })

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.headers).not.toHaveProperty('X-Organization-Id')
  })
})
