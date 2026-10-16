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
    })

    expect(window.localStorage.getItem('auth_user')).toBe(
      JSON.stringify({
        id: 7,
        username: 'ada',
        email: 'ada@example.com',
        avatarUrl: 'https://cdn.example.com/ada.png',
      }),
    )
    expect(useAuthStore.getState().user?.avatarUrl).toBe('https://cdn.example.com/ada.png')
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
    })
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
