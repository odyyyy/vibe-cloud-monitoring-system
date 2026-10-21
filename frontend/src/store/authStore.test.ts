import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './authStore'

function tokenWithClaims(claims: Record<string, unknown>): string {
  const encode = (value: unknown): string =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(claims)}.signature`
}

describe('authStore', () => {
  beforeEach(() => {
    window.localStorage.clear()
    useAuthStore.getState().logout()
  })

  it('persists the token in localStorage and derives the user on login', () => {
    const token = tokenWithClaims({ sub: 'ada', uid: 7 })

    useAuthStore.getState().login(token)

    expect(window.localStorage.getItem('token')).toBe(token)
    expect(useAuthStore.getState().token).toBe(token)
    expect(useAuthStore.getState().user).toEqual({
      id: 7,
      username: 'ada',
      email: null,
      avatarUrl: null,
      platformRole: null,
    })
  })

  it('persists the profile returned by setUser', () => {
    const token = tokenWithClaims({ sub: 'ada', uid: 7 })
    useAuthStore.getState().login(token)

    useAuthStore.getState().setUser({
      id: 7,
      username: 'ada',
      email: 'ada@example.com',
      avatarUrl: 'https://cdn.example.com/ada.png',
      platformRole: 'PLATFORM_ADMIN',
    })

    expect(window.localStorage.getItem('auth_user')).toBe(
      JSON.stringify({
        id: 7,
        username: 'ada',
        email: 'ada@example.com',
        avatarUrl: 'https://cdn.example.com/ada.png',
        platformRole: 'PLATFORM_ADMIN',
      }),
    )
    expect(useAuthStore.getState().user?.avatarUrl).toBe('https://cdn.example.com/ada.png')
    expect(useAuthStore.getState().user?.platformRole).toBe('PLATFORM_ADMIN')
  })

  it('rehydrates a stored user written before avatar support', async () => {
    vi.resetModules()
    window.localStorage.setItem('token', tokenWithClaims({ sub: 'ada', uid: 7 }))
    window.localStorage.setItem('auth_user', JSON.stringify({ id: 7, username: 'ada' }))

    const { useAuthStore: freshStore } = await import('./authStore')

    expect(freshStore.getState().user).toEqual({
      id: 7,
      username: 'ada',
      email: null,
      avatarUrl: null,
      platformRole: null,
    })
  })

  it('drops an unknown platform role when rehydrating a stored user', async () => {
    vi.resetModules()
    window.localStorage.setItem('token', tokenWithClaims({ sub: 'ada', uid: 7 }))
    window.localStorage.setItem(
      'auth_user',
      JSON.stringify({ id: 7, username: 'ada', platformRole: 'ROOT' }),
    )

    const { useAuthStore: freshStore } = await import('./authStore')

    expect(freshStore.getState().user?.platformRole).toBeNull()
  })

  it('persists the active organization and clears it on logout', () => {
    const token = tokenWithClaims({ sub: 'ada', uid: 7 })
    useAuthStore.getState().login(token)

    useAuthStore.getState().setActiveOrganization(42, 'ADMIN')

    expect(window.localStorage.getItem('auth_organization')).toBe('42')
    expect(useAuthStore.getState().currentOrganizationId).toBe(42)
    expect(useAuthStore.getState().currentRole).toBe('ADMIN')

    useAuthStore.getState().logout()

    expect(window.localStorage.getItem('auth_organization')).toBeNull()
    expect(useAuthStore.getState().currentOrganizationId).toBeNull()
    expect(useAuthStore.getState().currentRole).toBeNull()
  })

  it('clears a previously selected organization on login', () => {
    useAuthStore.getState().login(tokenWithClaims({ sub: 'ada', uid: 7 }))
    useAuthStore.getState().setActiveOrganization(42, 'OWNER')

    useAuthStore.getState().login(tokenWithClaims({ sub: 'grace', uid: 8 }))

    expect(window.localStorage.getItem('auth_organization')).toBeNull()
    expect(useAuthStore.getState().currentOrganizationId).toBeNull()
    expect(useAuthStore.getState().currentRole).toBeNull()
  })

  it('rehydrates a stored active organization id', async () => {
    vi.resetModules()
    window.localStorage.setItem('token', tokenWithClaims({ sub: 'ada', uid: 7 }))
    window.localStorage.setItem('auth_organization', '42')

    const { useAuthStore: freshStore } = await import('./authStore')

    expect(freshStore.getState().currentOrganizationId).toBe(42)
    expect(freshStore.getState().currentRole).toBeNull()
  })

  it('clears the session on logout', () => {
    const token = tokenWithClaims({ sub: 'ada', uid: 7 })
    useAuthStore.getState().login(token)

    useAuthStore.getState().logout()

    expect(window.localStorage.getItem('token')).toBeNull()
    expect(window.localStorage.getItem('auth_user')).toBeNull()
    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
  })
})
