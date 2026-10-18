import { useAuthStore } from '../store/authStore'

const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const DEFAULT_ERROR_MESSAGE = 'Request failed. Please try again.'

interface ErrorBody {
  message?: string
  detail?: string
  error?: string
  title?: string
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  token?: string | null
  signal?: AbortSignal
}

function extractMessage(body: unknown): string | undefined {
  if (typeof body !== 'object' || body === null) {
    return undefined
  }
  const candidate = body as ErrorBody
  return candidate.message ?? candidate.detail ?? candidate.error ?? candidate.title
}

async function parseBody(response: Response): Promise<unknown> {
  const text = await response.text()
  if (text.length === 0) {
    return undefined
  }
  try {
    return JSON.parse(text) as unknown
  } catch {
    return undefined
  }
}

export async function apiRequest<TResponse>(path: string, options: RequestOptions = {}): Promise<TResponse> {
  const { method = 'GET', body, token, signal } = options

  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const organizationId = useAuthStore.getState().currentOrganizationId
  if (organizationId !== null) {
    headers['X-Organization-Id'] = String(organizationId)
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch {
    throw new ApiError(0, 'Unable to reach the server. Please try again.')
  }

  const parsed = await parseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, extractMessage(parsed) ?? DEFAULT_ERROR_MESSAGE)
  }

  return parsed as TResponse
}
