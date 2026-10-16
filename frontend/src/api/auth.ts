import { apiRequest } from './client'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface AuthResponse {
  token: string
}

export interface UserProfile {
  id: number
  username: string
  email: string | null
  avatarUrl: string | null
}

export function login(payload: LoginRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/auth/login', { method: 'POST', body: payload })
}

export function register(payload: RegisterRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/auth/register', { method: 'POST', body: payload })
}

export function getProfile(token: string): Promise<UserProfile> {
  return apiRequest<UserProfile>('/auth/me', { token })
}
