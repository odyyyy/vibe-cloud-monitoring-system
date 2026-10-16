import { create } from 'zustand'
import { decodeJwt } from '../lib/jwt'

const TOKEN_STORAGE_KEY = 'token'
const USER_STORAGE_KEY = 'auth_user'

export interface AuthUser {
  id: number | null
  username: string
  email: string | null
  avatarUrl: string | null
}

interface AuthState {
  user: AuthUser | null
  token: string | null
  login: (token: string) => void
  setUser: (user: AuthUser) => void
  logout: () => void
}

function normalizeUser(value: unknown): AuthUser | null {
  if (typeof value !== 'object' || value === null) {
    return null
  }
  const candidate = value as Partial<AuthUser>
  if (typeof candidate.username !== 'string') {
    return null
  }
  return {
    id: typeof candidate.id === 'number' ? candidate.id : null,
    username: candidate.username,
    email: typeof candidate.email === 'string' ? candidate.email : null,
    avatarUrl: typeof candidate.avatarUrl === 'string' ? candidate.avatarUrl : null,
  }
}

function toUser(token: string): AuthUser | null {
  const claims = decodeJwt(token)
  if (!claims?.sub) {
    return null
  }
  return {
    id: typeof claims.uid === 'number' ? claims.uid : null,
    username: claims.sub,
    email: null,
    avatarUrl: null,
  }
}

function readStoredToken(): string | null {
  try {
    return window.localStorage.getItem(TOKEN_STORAGE_KEY)
  } catch {
    return null
  }
}

function readStoredUser(token: string | null): AuthUser | null {
  if (!token) {
    return null
  }
  try {
    const raw = window.localStorage.getItem(USER_STORAGE_KEY)
    if (!raw) {
      return toUser(token)
    }
    return normalizeUser(JSON.parse(raw) as unknown) ?? toUser(token)
  } catch {
    return toUser(token)
  }
}

function persistUser(user: AuthUser | null): void {
  try {
    if (user) {
      window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user))
    } else {
      window.localStorage.removeItem(USER_STORAGE_KEY)
    }
  } catch {
    void 0
  }
}

const initialToken = readStoredToken()

export const useAuthStore = create<AuthState>((set) => ({
  token: initialToken,
  user: readStoredUser(initialToken),
  login: (token) => {
    const user = toUser(token)
    try {
      window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
    } catch {
      void 0
    }
    persistUser(user)
    set({ token, user })
  },
  setUser: (user) => {
    persistUser(user)
    set({ user })
  },
  logout: () => {
    try {
      window.localStorage.removeItem(TOKEN_STORAGE_KEY)
    } catch {
      void 0
    }
    persistUser(null)
    set({ token: null, user: null })
  },
}))
