import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export interface Application {
  id: number
  name: string
  apiKey: string
  createdAt: string
}

export interface CreateApplicationRequest {
  name: string
}

export interface RegenerateApiKeyResponse {
  apiKey: string
}

const BASE_PATH = '/applications'

function authToken(): string | null {
  return useAuthStore.getState().token
}

export function getApplications(): Promise<Application[]> {
  return apiRequest<Application[]>(BASE_PATH, { token: authToken() })
}

export function createApplication(data: CreateApplicationRequest): Promise<Application> {
  return apiRequest<Application>(BASE_PATH, { method: 'POST', body: data, token: authToken() })
}

export function getApplication(id: number): Promise<Application> {
  return apiRequest<Application>(`${BASE_PATH}/${id}`, { token: authToken() })
}

export function updateApplication(id: number, data: CreateApplicationRequest): Promise<Application> {
  return apiRequest<Application>(`${BASE_PATH}/${id}`, { method: 'PUT', body: data, token: authToken() })
}

export function deleteApplication(id: number): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}`, { method: 'DELETE', token: authToken() })
}

/**
 * The backend returns the full updated application (including the new key), not
 * a bare `{ apiKey }` payload. The response is normalized to the documented
 * `RegenerateApiKeyResponse` shape so callers stay decoupled from the backend.
 */
export async function regenerateApiKey(id: number): Promise<RegenerateApiKeyResponse> {
  const application = await apiRequest<Application>(`${BASE_PATH}/${id}/regenerate-api-key`, {
    method: 'POST',
    token: authToken(),
  })
  return { apiKey: application.apiKey }
}
