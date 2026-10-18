import { create } from 'zustand'

interface OnboardingState {
  isOpen: boolean
  openWizard: () => void
  closeWizard: () => void
}

/**
 * Shared open/closed state for the onboarding wizard. The wizard itself decides what to show
 * based on the current workspace; the sidebar button and the auto-open hook only toggle this.
 */
export const useOnboardingStore = create<OnboardingState>((set) => ({
  isOpen: false,
  openWizard: () => set({ isOpen: true }),
  closeWizard: () => set({ isOpen: false }),
}))
