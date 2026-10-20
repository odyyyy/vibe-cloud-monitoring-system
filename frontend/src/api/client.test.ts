import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../store/authStore'
import {
  ApiError,
  apiDownload,
  apiRequest,
  parseContentDispositionFilename,
} from './client'

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

describe('apiDownload', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    window.localStorage.clear()
    useAuthStore.getState().logout()
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns the file, filename and truncation flag', async () => {
    useAuthStore.getState().login(tokenWithClaims({ sub: 'ada', uid: 7 }))
    useAuthStore.getState().setActiveOrganization(42, 'VIEWER')
    fetchMock.mockResolvedValue(downloadResponse())

    const result = await apiDownload('/v1/logs?format=csv', { token: 'jwt-token' })

    expect(result.filename).toBe('export.csv')
    expect(result.truncated).toBe(true)
    expect(result.blob).toBeInstanceOf(Blob)

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.headers).toMatchObject({
      Accept: '*/*',
      Authorization: 'Bearer jwt-token',
      'X-Organization-Id': '42',
    })
  })

  it('throws ApiError with the server message for error responses', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 403,
      text: async () => '{"message":"You do not have access to this application"}',
    } as Response)

    await expect(apiDownload('/v1/logs?format=csv', { token: 'jwt-token' }))
      .rejects.toEqual(new ApiError(403, 'You do not have access to this application'))
  })
})

describe('parseContentDispositionFilename', () => {
  it('reads a quoted filename', () => {
    expect(parseContentDispositionFilename('attachment; filename="export.csv"')).toBe('export.csv')
  })

  it('reads an unquoted filename', () => {
    expect(parseContentDispositionFilename('attachment; filename=export.json')).toBe('export.json')
  })

  it('returns null when the header is missing or has no filename', () => {
    expect(parseContentDispositionFilename(null)).toBeNull()
    expect(parseContentDispositionFilename('attachment')).toBeNull()
  })
})

function downloadResponse(): Response {
  return {
    ok: true,
    status: 200,
    blob: async () => new Blob(['id,message']),
    headers: {
      get: (name: string) => {
        const key = name.toLowerCase()
        if (key === 'content-disposition') {
          return 'attachment; filename="export.csv"'
        }
        if (key === 'x-export-truncated') {
          return 'true'
        }
        return null
      },
    },
  } as Response
}
