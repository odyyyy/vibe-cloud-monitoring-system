import { beforeEach, describe, expect, it } from 'vitest'
import { onboardingStorageKey, readOnboardingStatus, writeOnboardingStatus } from './storage'

describe('onboarding storage', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('returns null for a user without a stored status', () => {
    expect(readOnboardingStatus(7)).toBeNull()
  })

  it('round-trips the completed status', () => {
    writeOnboardingStatus(7, 'completed')
    expect(readOnboardingStatus(7)).toBe('completed')
  })

  it('round-trips the dismissed status', () => {
    writeOnboardingStatus(7, 'dismissed')
    expect(readOnboardingStatus(7)).toBe('dismissed')
  })

  it('keeps statuses isolated per user', () => {
    writeOnboardingStatus(7, 'completed')
    expect(readOnboardingStatus(8)).toBeNull()
  })

  it('uses a distinct key for an unknown user id', () => {
    writeOnboardingStatus(null, 'dismissed')
    expect(onboardingStorageKey(null)).toBe('onboarding:v1:anonymous')
    expect(readOnboardingStatus(null)).toBe('dismissed')
    expect(readOnboardingStatus(0)).toBeNull()
  })

  it('ignores unknown or corrupted stored values', () => {
    window.localStorage.setItem(onboardingStorageKey(7), 'not-a-status')
    expect(readOnboardingStatus(7)).toBeNull()
  })
})
