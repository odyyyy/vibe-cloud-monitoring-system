import { apiRequest } from './client'
import type { MessageResponse, UserProfile } from './auth'

export interface UsernameAvailability {
  available: boolean
}

export interface UpdateUsernamePayload {
  username: string
  currentPassword: string
}

export interface RequestEmailChangePayload {
  newEmail: string
  currentPassword: string
}

export function updateUsername(token: string, payload: UpdateUsernamePayload): Promise<UserProfile> {
  return apiRequest<UserProfile>('/profile/username', { method: 'PUT', body: payload, token })
}

export function checkUsernameAvailable(token: string, username: string): Promise<UsernameAvailability> {
  return apiRequest<UsernameAvailability>(
    `/profile/username-available?username=${encodeURIComponent(username)}`,
    { token },
  )
}

export function requestEmailChange(token: string, payload: RequestEmailChangePayload): Promise<MessageResponse> {
  return apiRequest<MessageResponse>('/profile/email', { method: 'PUT', body: payload, token })
}

export function confirmEmailChange(token: string): Promise<MessageResponse> {
  return apiRequest<MessageResponse>('/auth/email-change/confirm', { method: 'POST', body: { token } })
}
