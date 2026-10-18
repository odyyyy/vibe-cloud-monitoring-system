import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DEFAULT_THEME } from '../features/theme/themes'
import { useThemeStore } from './themeStore'

describe('themeStore', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('defaults to midnight and applies it on load', async () => {
    vi.resetModules()

    const { useThemeStore: freshStore } = await import('./themeStore')

    expect(freshStore.getState().theme).toBe(DEFAULT_THEME)
    expect(document.documentElement.getAttribute('data-theme')).toBe(DEFAULT_THEME)
  })

  it('persists and applies the selected theme', () => {
    useThemeStore.getState().setTheme('daylight')

    expect(useThemeStore.getState().theme).toBe('daylight')
    expect(window.localStorage.getItem('theme')).toBe('daylight')
    expect(document.documentElement.getAttribute('data-theme')).toBe('daylight')
  })

  it('rehydrates a stored theme on load', async () => {
    vi.resetModules()
    window.localStorage.setItem('theme', 'aurora')

    const { useThemeStore: freshStore } = await import('./themeStore')

    expect(freshStore.getState().theme).toBe('aurora')
    expect(document.documentElement.getAttribute('data-theme')).toBe('aurora')
  })

  it('falls back to the default for an unknown stored value', async () => {
    vi.resetModules()
    window.localStorage.setItem('theme', 'not-a-theme')

    const { useThemeStore: freshStore } = await import('./themeStore')

    expect(freshStore.getState().theme).toBe(DEFAULT_THEME)
  })
})
