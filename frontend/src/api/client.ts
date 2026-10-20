import { useAuthStore } from '../store/authStore'

const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const DEFAULT_ERROR_MESSAGE = 'Request failed. Please try again.'

export function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`
}

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

export type ExportFormat = 'csv' | 'json'

export interface DownloadResponse {
  blob: Blob
  filename: string | null
  /** True when the server truncated the result to its export row limit. */
  truncated: boolean
}

export const EXPORT_TRUNCATED_HEADER = 'X-Export-Truncated'

function buildHeaders(token: string | null | undefined, accept: string): Record<string, string> {
  const headers: Record<string, string> = { Accept: accept }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const organizationId = useAuthStore.getState().currentOrganizationId
  if (organizationId !== null) {
    headers['X-Organization-Id'] = String(organizationId)
  }
  return headers
}

export function parseContentDispositionFilename(header: string | null): string | null {
  if (!header) {
    return null
  }
  const match = /filename\*?=(?:UTF-8'')?("?)([^";]+)\1/i.exec(header)
  if (!match) {
    return null
  }
  const raw = match[2].trim()
  try {
    return decodeURIComponent(raw)
  } catch {
    return raw
  }
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

  const headers = buildHeaders(token, 'application/json')
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  let response: Response
  try {
    response = await fetch(apiUrl(path), {
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

/**
 * Downloads a file (for example a CSV/JSON export) with the same authentication and tenant
 * headers as {@link apiRequest}. Error responses are still parsed as JSON so callers can show
 * the server message.
 */
export async function apiDownload(
  path: string,
  options: Omit<RequestOptions, 'method' | 'body'> = {},
): Promise<DownloadResponse> {
  const { token, signal } = options

  let response: Response
  try {
    response = await fetch(apiUrl(path), {
      method: 'GET',
      headers: buildHeaders(token, '*/*'),
      signal,
    })
  } catch {
    throw new ApiError(0, 'Unable to reach the server. Please try again.')
  }

  if (!response.ok) {
    throw new ApiError(response.status, extractMessage(await parseBody(response)) ?? DEFAULT_ERROR_MESSAGE)
  }

  return {
    blob: await response.blob(),
    filename: parseContentDispositionFilename(response.headers.get('Content-Disposition')),
    truncated: response.headers.get(EXPORT_TRUNCATED_HEADER) === 'true',
  }
}
