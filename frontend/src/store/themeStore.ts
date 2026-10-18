import { create } from 'zustand'
import { applyTheme, DEFAULT_THEME, isThemeId, type ThemeId } from '../features/theme/themes'

const THEME_STORAGE_KEY = 'theme'

function readStoredTheme(): ThemeId {
  try {
    const raw = window.localStorage.getItem(THEME_STORAGE_KEY)
    return isThemeId(raw) ? raw : DEFAULT_THEME
  } catch {
    return DEFAULT_THEME
  }
}

interface ThemeState {
  theme: ThemeId
  setTheme: (theme: ThemeId) => void
}

const initialTheme = readStoredTheme()
// Apply before the first render so the stored theme has no flash of the default.
applyTheme(initialTheme)

export const useThemeStore = create<ThemeState>((set) => ({
  theme: initialTheme,
  setTheme: (theme) => {
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, theme)
    } catch {
      void 0
    }
    applyTheme(theme)
    set({ theme })
  },
}))
