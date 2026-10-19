import { describe, expect, it } from 'vitest'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../api/client'
import { getAuthErrorMessage } from './errors'

describe('getAuthErrorMessage', () => {
  it('maps 401 to invalid credentials on login', () => {
    expect(getAuthErrorMessage(new ApiError(401, ''), 'login')).toBe('Invalid username or password.')
  })

  it('maps 404 to invalid credentials on login', () => {
    expect(getAuthErrorMessage(new ApiError(404, ''), 'login')).toBe('Invalid username or password.')
  })

  it('maps 403 on login to an unconfirmed-email message', () => {
    expect(getAuthErrorMessage(new ApiError(403, ''), 'login')).toBe(
      'Please confirm your email address before signing in.',
    )
  })

  it('maps 429 on login to a rate-limit message', () => {
    expect(getAuthErrorMessage(new ApiError(429, ''), 'login')).toBe(
      'Too many failed sign-in attempts. Please wait a few minutes and try again.',
    )
  })

  it('maps 409 to a duplicate-account message on register', () => {
    expect(getAuthErrorMessage(new ApiError(409, ''), 'register')).toBe(
      'This username or email is already in use.',
    )
  })

  it('maps a generic 400 on register to a duplicate-account message', () => {
    expect(getAuthErrorMessage(new ApiError(400, DEFAULT_ERROR_MESSAGE), 'register')).toBe(
      'This username or email is already in use.',
    )
  })

  it('keeps specific backend messages when present', () => {
    expect(getAuthErrorMessage(new ApiError(400, 'Email is invalid'), 'register')).toBe('Email is invalid')
  })

  it('maps network failures', () => {
    expect(getAuthErrorMessage(new ApiError(0, 'Unable to reach the server.'), 'login')).toBe(
      'Unable to reach the server.',
    )
  })

  it('handles non-ApiError values', () => {
    expect(getAuthErrorMessage(new Error('boom'), 'login')).toBe('Something went wrong. Please try again.')
  })

  it('keeps specific backend messages on forgot password', () => {
    expect(getAuthErrorMessage(new ApiError(400, 'Email is invalid'), 'forgot')).toBe('Email is invalid')
  })

  it('maps server failures on forgot password', () => {
    expect(getAuthErrorMessage(new ApiError(500, 'boom'), 'forgot')).toBe(
      'The server is unavailable right now. Please try again later.',
    )
  })

  it('maps a generic 400 on reset to an expired-link message', () => {
    expect(getAuthErrorMessage(new ApiError(400, DEFAULT_ERROR_MESSAGE), 'reset')).toBe(
      'This reset link is invalid or has expired. Please request a new one.',
    )
  })

  it('keeps specific backend messages on reset', () => {
    expect(getAuthErrorMessage(new ApiError(400, 'Invalid or expired token'), 'reset')).toBe(
      'Invalid or expired token',
    )
  })
})
