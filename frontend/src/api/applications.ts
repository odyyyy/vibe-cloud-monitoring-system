import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export interface Application {
  id: number
  name: string
  createdAt: string
}

/**
 * Returned only when a key is issued (create / regenerate). The backend stores just a hash,
 * so the plaintext key is available exactly once and is never returned by list/get again.
 */
export interface CreatedApplication extends Application {
  apiKey: string
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

export function createApplication(data: CreateApplicationRequest): Promise<CreatedApplication> {
  return apiRequest<CreatedApplication>(BASE_PATH, { method: 'POST', body: data, token: authToken() })
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
 * The backend returns the created application with the new plaintext key. The response is
 * normalized to the documented `RegenerateApiKeyResponse` shape so callers stay decoupled
 * from the backend resource representation.
 */
export async function regenerateApiKey(id: number): Promise<RegenerateApiKeyResponse> {
  const application = await apiRequest<CreatedApplication>(`${BASE_PATH}/${id}/regenerate-api-key`, {
    method: 'POST',
    token: authToken(),
  })
  return { apiKey: application.apiKey }
}
