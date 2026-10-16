import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../api/client'

export type AuthContext = 'login' | 'register' | 'forgot' | 'reset' | 'resend'

export function getAuthErrorMessage(error: unknown, context: AuthContext): string {
  if (!(error instanceof ApiError)) {
    return 'Something went wrong. Please try again.'
  }

  if (error.status === 0) {
    return error.message
  }

  if (context === 'login') {
    if (error.status === 403) {
      return 'Please confirm your email address before signing in.'
    }
    if (error.status === 401 || error.status === 404) {
      return 'Invalid username or password.'
    }
    if (error.status === 400) {
      return 'Please check your credentials and try again.'
    }
    if (error.status >= 500) {
      return 'The server is unavailable right now. Please try again later.'
    }
    return error.message
  }

  if (context === 'forgot' || context === 'resend') {
    if (error.status === 400) {
      return error.message
    }
    if (error.status >= 500) {
      return 'The server is unavailable right now. Please try again later.'
    }
    return error.message
  }

  if (context === 'reset') {
    if (error.status === 400) {
      return error.message === DEFAULT_ERROR_MESSAGE
        ? 'This reset link is invalid or has expired. Please request a new one.'
        : error.message
    }
    if (error.status >= 500) {
      return 'The server is unavailable right now. Please try again later.'
    }
    return error.message
  }

  if (error.status === 409) {
    return 'This username or email is already in use.'
  }
  if (error.status === 400) {
    return error.message === DEFAULT_ERROR_MESSAGE
      ? 'This username or email is already in use.'
      : error.message
  }
  if (error.status >= 500) {
    return 'Registration failed. The username or email may already be in use.'
  }
  return error.message
}
