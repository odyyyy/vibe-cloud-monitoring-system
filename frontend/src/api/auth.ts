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

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface AuthResponse {
  token: string
}

export interface MessageResponse {
  message: string
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

export function register(payload: RegisterRequest): Promise<MessageResponse> {
  return apiRequest<MessageResponse>('/auth/register', { method: 'POST', body: payload })
}

export function verifyEmail(token: string): Promise<MessageResponse> {
  return apiRequest<MessageResponse>('/auth/verify-email', { method: 'POST', body: { token } })
}

export function resendVerification(email: string): Promise<MessageResponse> {
  return apiRequest<MessageResponse>('/auth/resend-verification', { method: 'POST', body: { email } })
}

export function forgotPassword(email: string): Promise<MessageResponse> {
  return apiRequest<MessageResponse>('/auth/forgot-password', { method: 'POST', body: { email } })
}

export function resetPassword(payload: ResetPasswordRequest): Promise<MessageResponse> {
  return apiRequest<MessageResponse>('/auth/reset-password', { method: 'POST', body: payload })
}

export function getProfile(token: string): Promise<UserProfile> {
  return apiRequest<UserProfile>('/auth/me', { token })
}
