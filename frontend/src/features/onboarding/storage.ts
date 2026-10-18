export type OnboardingStatus = 'completed' | 'dismissed'

const STATUS_VALUES: OnboardingStatus[] = ['completed', 'dismissed']
const STORAGE_PREFIX = 'onboarding:v1:'

/** Per-user key so switching accounts in the same browser does not skip onboarding. */
export function onboardingStorageKey(userId: number | null): string {
  return `${STORAGE_PREFIX}${userId ?? 'anonymous'}`
}

function isOnboardingStatus(value: string | null): value is OnboardingStatus {
  return value !== null && (STATUS_VALUES as string[]).includes(value)
}

/**
 * Reads whether this user already finished or dismissed the wizard. Returns `null` when
 * onboarding should still be offered automatically (fresh user or unreadable storage).
 */
export function readOnboardingStatus(userId: number | null): OnboardingStatus | null {
  try {
    const raw = window.localStorage.getItem(onboardingStorageKey(userId))
    return isOnboardingStatus(raw) ? raw : null
  } catch {
    return null
  }
}

/** Records that the wizard will not be reopened automatically for this user. */
export function writeOnboardingStatus(userId: number | null, status: OnboardingStatus): void {
  try {
    window.localStorage.setItem(onboardingStorageKey(userId), status)
  } catch {
    void 0
  }
}
