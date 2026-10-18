import { create } from 'zustand'
import { decodeJwt } from '../lib/jwt'

const TOKEN_STORAGE_KEY = 'token'
const USER_STORAGE_KEY = 'auth_user'
const ORGANIZATION_STORAGE_KEY = 'auth_organization'

export type MembershipRole = 'OWNER' | 'ADMIN' | 'VIEWER'

export interface AuthUser {
  id: number | null
  username: string
  email: string | null
  avatarUrl: string | null
}

interface AuthState {
  user: AuthUser | null
  token: string | null
  currentOrganizationId: number | null
  currentRole: MembershipRole | null
  login: (token: string) => void
  setUser: (user: AuthUser) => void
  setActiveOrganization: (organizationId: number, role: MembershipRole) => void
  clearActiveOrganization: () => void
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

function readStoredOrganization(token: string | null): number | null {
  if (!token) {
    return null
  }
  try {
    const raw = window.localStorage.getItem(ORGANIZATION_STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = Number(raw)
    return Number.isInteger(parsed) && parsed > 0 ? parsed : null
  } catch {
    return null
  }
}

function persistOrganization(organizationId: number | null): void {
  try {
    if (organizationId === null) {
      window.localStorage.removeItem(ORGANIZATION_STORAGE_KEY)
    } else {
      window.localStorage.setItem(ORGANIZATION_STORAGE_KEY, String(organizationId))
    }
  } catch {
    void 0
  }
}

const initialToken = readStoredToken()

export const useAuthStore = create<AuthState>((set) => ({
  token: initialToken,
  user: readStoredUser(initialToken),
  currentOrganizationId: readStoredOrganization(initialToken),
  currentRole: null,
  login: (token) => {
    const user = toUser(token)
    try {
      window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
    } catch {
      void 0
    }
    persistUser(user)
    persistOrganization(null)
    set({ token, user, currentOrganizationId: null, currentRole: null })
  },
  setUser: (user) => {
    persistUser(user)
    set({ user })
  },
  setActiveOrganization: (organizationId, role) => {
    persistOrganization(organizationId)
    set({ currentOrganizationId: organizationId, currentRole: role })
  },
  clearActiveOrganization: () => {
    persistOrganization(null)
    set({ currentOrganizationId: null, currentRole: null })
  },
  logout: () => {
    try {
      window.localStorage.removeItem(TOKEN_STORAGE_KEY)
    } catch {
      void 0
    }
    persistUser(null)
    persistOrganization(null)
    set({ token: null, user: null, currentOrganizationId: null, currentRole: null })
  },
}))
